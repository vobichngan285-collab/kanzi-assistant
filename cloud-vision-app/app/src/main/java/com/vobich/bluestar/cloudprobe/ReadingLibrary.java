package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Private, atomic, text-only on-device book library. No photos, audio or API keys persisted. */
final class ReadingLibrary {
    static final class Page {
        final int number;
        final String text,chapter;
        final long at;
        Page(int number,String text,String chapter,long at){
            this.number=number;this.text=text;this.chapter=chapter;this.at=at;
        }
    }
    static final class Book {
        final String id,title;
        final long created,updated;
        final List<Page> pages;
        Book(String id,String title,long created,long updated,List<Page> pages){
            this.id=id;this.title=title;this.created=created;this.updated=updated;
            this.pages=Collections.unmodifiableList(new ArrayList<>(pages));
        }
        Page lastPage(){return pages.isEmpty()?null:pages.get(pages.size()-1);}
    }
    private final File dir;
    ReadingLibrary(Context context){
        dir=new File(context.getFilesDir(),"reading_books");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("无法创建书架目录");
    }
    private File path(String id){
        if(id==null||!id.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("无效书籍标识");
        return new File(dir,"book_"+id+".json");
    }
    private Book read(String id) throws Exception {
        File file=path(id);
        if(!file.isFile())throw new IllegalStateException("书籍不存在");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(FileInputStream in=new AtomicFile(file).openRead()){
            byte[] chunk=new byte[8192];
            int n;
            while((n=in.read(chunk))!=-1){
                if(bytes.size()+n>24*1024*1024)throw new IllegalStateException("书籍文件过大");
                bytes.write(chunk,0,n);
            }
        }
        JSONObject data=new JSONObject(bytes.toString("UTF-8"));
        JSONArray records=data.getJSONArray("pages");
        ArrayList<Page> pages=new ArrayList<>();
        for(int i=0;i<records.length();i++){
            JSONObject p=records.getJSONObject(i);
            String text=p.getString("text");
            if(!ReadingRules.usable(text))throw new IllegalStateException("书页内容已损坏");
            pages.add(new Page(p.getInt("number"),text,p.optString("chapter",""),p.optLong("at",0)));
        }
        return new Book(data.getString("id"),data.getString("title"),data.getLong("created"),
            data.optLong("updated",0),pages);
    }
    synchronized Book get(String id) throws Exception {return read(id);}
    synchronized List<Book> list() throws Exception {
        File[] files=dir.listFiles((d,n)->n.startsWith("book_")&&n.endsWith(".json"));
        if(files==null)throw new IllegalStateException("书架目录无法读取");
        ArrayList<Book> all=new ArrayList<>();
        for(File file:files){
            String name=file.getName();
            all.add(read(name.substring(5,name.length()-5)));
        }
        all.sort((a,b)->Long.compare(b.updated,a.updated));
        return all;
    }
    synchronized Book create(String name) throws Exception {
        String title=ReadingRules.title(name);
        if(title.isEmpty())throw new IllegalArgumentException("请填写书名");
        String id=UUID.randomUUID().toString().replace("-","");
        long now=System.currentTimeMillis();
        Book result=new Book(id,title,now,now,Collections.emptyList());
        write(result);
        return result;
    }
    synchronized Page add(String id,String text) throws Exception {
        if(!ReadingRules.usable(text))throw new IllegalArgumentException("书页为空或过长");
        Book prior=read(id);
        Page last=prior.lastPage();
        String clean=text.trim();
        if(last!=null&&last.text.equals(clean))
            throw new IllegalStateException("检测到重复的上一页，已阻止重复保存");
        String chapter=ReadingRules.chapter(clean);
        if(chapter.isEmpty()&&last!=null)chapter=last.chapter;
        Page next=new Page(prior.pages.size()+1,clean,chapter,System.currentTimeMillis());
        ArrayList<Page> pages=new ArrayList<>(prior.pages);pages.add(next);
        write(new Book(prior.id,prior.title,prior.created,System.currentTimeMillis(),pages));
        return next;
    }
    synchronized void delete(String id) throws Exception {
        File file=path(id);
        if(!file.exists())throw new IllegalStateException("书籍不存在");
        if(!file.delete())throw new IllegalStateException("无法删除书籍");
    }
    synchronized String export(String id) throws Exception {
        Book book=read(id);
        StringBuilder text=new StringBuilder(book.title).append("\n\n");
        for(Page page:book.pages){
            text.append("第").append(page.number).append("页");
            if(!page.chapter.isEmpty())text.append(" · ").append(page.chapter);
            text.append("\n").append(page.text).append("\n\n");
        }
        return text.toString();
    }
    private void write(Book book) throws Exception {
        JSONObject data=new JSONObject();
        data.put("id",book.id);data.put("title",book.title);
        data.put("created",book.created);data.put("updated",book.updated);
        JSONArray pages=new JSONArray();
        for(Page page:book.pages){
            JSONObject p=new JSONObject();
            p.put("number",page.number);p.put("text",page.text);
            p.put("chapter",page.chapter);p.put("at",page.at);
            pages.put(p);
        }
        data.put("pages",pages);
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>24*1024*1024)throw new IllegalStateException("整本书过大，请先导出备份");
        AtomicFile target=new AtomicFile(path(book.id));
        FileOutputStream out=null;
        try{
            out=target.startWrite();
            out.write(bytes);
            target.finishWrite(out);
        }catch(Exception failure){
            if(out!=null)target.failWrite(out);
            throw failure;
        }
    }
}
