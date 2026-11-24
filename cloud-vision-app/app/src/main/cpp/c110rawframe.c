// Experimental libVLC 3.x memory output. No Android Surface or screen capture.
#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <errno.h>
typedef void* P;
typedef void* (*lock_cb)(void*,void**);
typedef void (*unlock_cb)(void*,void*,void*const*);
typedef void (*display_cb)(void*,void*);
typedef unsigned (*format_cb)(void**,char*,unsigned*,unsigned*,unsigned*,unsigned*);
typedef void (*cleanup_cb)(void*);
typedef struct {
 P so,instance,player;
 P (*new_instance)(int,const char*const*);
 void (*release_instance)(P);
 P (*new_media)(P,const char*);
 void (*release_media)(P);
 P (*new_player)(P);
 void (*release_player)(P);
 void (*set_media)(P,P);
 int (*play)(P);
 void (*stop)(P);
 void (*callbacks)(P,lock_cb,unlock_cb,display_cb,void*);
 void (*formats)(P,format_cb,cleanup_cb);
 pthread_mutex_t mu;
 pthread_cond_t cv;
 unsigned w,h,pitch,lines,shot_w,shot_h;
 uint8_t *pixels,*shot;
 unsigned frames,shot_frames;
 int wanted,ready,failed;
} Decoder;
static unsigned align32(unsigned n){return (n+31u)&~31u;}
static unsigned format(void **opaque,char *chroma,unsigned *w,unsigned *h,unsigned *pitches,unsigned *lines){
 Decoder*d=*opaque;
 if(*w<2||*h<2||*w>4096||*h>4096||(uint64_t)*w**h>9000000u)return 0;
 unsigned pitch=align32(*w*4), rows=align32(*h);
 void *buf=NULL;
 if(posix_memalign(&buf,32,(size_t)pitch*rows)!=0)return 0;
 memset(buf,0,(size_t)pitch*rows);
 pthread_mutex_lock(&d->mu);
 free(d->pixels);d->pixels=buf;d->w=*w;d->h=*h;d->pitch=pitch;d->lines=rows;
 pthread_mutex_unlock(&d->mu);
 memcpy(chroma,"RGBA",4);pitches[0]=pitch;lines[0]=rows;
 return 1; // one buffer; VLC owns its lock/unlock lifecycle
}
static void cleanup(void *opaque){
 Decoder*d=opaque;pthread_mutex_lock(&d->mu);
 free(d->pixels);d->pixels=NULL;d->w=d->h=0;
 pthread_mutex_unlock(&d->mu);
}
static void *lock_frame(void *opaque,void **planes){
 Decoder*d=opaque;planes[0]=d->pixels;return d;
}
static void unlock_frame(void *opaque,void *picture,void *const *planes){
 (void)picture;(void)planes;Decoder*d=opaque;
 pthread_mutex_lock(&d->mu);d->frames++;
 if(d->wanted&&!d->ready&&!d->failed&&d->pixels){
  size_t count=(size_t)d->w*d->h*4;
  d->shot=malloc(count);
  if(!d->shot)d->failed=1;
  else {
   for(unsigned y=0;y<d->h;y++)
    memcpy(d->shot+(size_t)y*d->w*4,d->pixels+(size_t)y*d->pitch,(size_t)d->w*4);
   d->shot_w=d->w;d->shot_h=d->h;d->shot_frames=d->frames;d->ready=1;
  }
  pthread_cond_signal(&d->cv);
 }
 pthread_mutex_unlock(&d->mu);
}
static void close_decoder(Decoder*d){
 if(!d)return;
 if(d->player){d->stop(d->player);d->release_player(d->player);}
 if(d->instance)d->release_instance(d->instance);
 free(d->pixels);free(d->shot);
 pthread_cond_destroy(&d->cv);pthread_mutex_destroy(&d->mu);
 if(d->so)dlclose(d->so);
 free(d);
}
#define LOAD(member,sym) do { *(void**)(&d->member)=dlsym(d->so,sym);if(!d->member)goto fail; } while(0)
JNIEXPORT jlong JNICALL Java_com_vobich_bluestar_cloudprobe_C110NativeFrameDecoder_open(JNIEnv*env,jclass cls){
 (void)env;(void)cls;
 Decoder*d=calloc(1,sizeof(*d));if(!d)return 0;
 pthread_mutex_init(&d->mu,NULL);pthread_cond_init(&d->cv,NULL);
 d->so=dlopen("libvlc.so",RTLD_NOW|RTLD_LOCAL);if(!d->so)goto fail;
 LOAD(new_instance,"libvlc_new");LOAD(release_instance,"libvlc_release");
 LOAD(new_media,"libvlc_media_new_location");LOAD(release_media,"libvlc_media_release");
 LOAD(new_player,"libvlc_media_player_new");LOAD(release_player,"libvlc_media_player_release");
 LOAD(set_media,"libvlc_media_player_set_media");LOAD(play,"libvlc_media_player_play");
 LOAD(stop,"libvlc_media_player_stop");LOAD(callbacks,"libvlc_video_set_callbacks");
 LOAD(formats,"libvlc_video_set_format_callbacks");
 const char* args[]={"--no-audio","--no-video-title-show","--avcodec-hw=none","--network-caching=150","--live-caching=150"};
 d->instance=d->new_instance(sizeof(args)/sizeof(args[0]),args);if(!d->instance)goto fail;
 d->player=d->new_player(d->instance);if(!d->player)goto fail;
 P media=d->new_media(d->instance,"rtsp://192.168.1.254:554/xxx.mp4");
 if(!media)goto fail;
 d->set_media(d->player,media);d->release_media(media);
 d->callbacks(d->player,lock_frame,unlock_frame,NULL,d);
 d->formats(d->player,format,cleanup);
 if(d->play(d->player)!=0)goto fail;
 return (jlong)(intptr_t)d;
fail:close_decoder(d);return 0;
}
JNIEXPORT jintArray JNICALL Java_com_vobich_bluestar_cloudprobe_C110NativeFrameDecoder_capture(JNIEnv*env,jclass cls,jlong handle){
 (void)cls;Decoder*d=(Decoder*)(intptr_t)handle;if(!d)return NULL;
 struct timespec deadline;clock_gettime(CLOCK_REALTIME,&deadline);deadline.tv_sec+=2;
 pthread_mutex_lock(&d->mu);
 free(d->shot);d->shot=NULL;d->wanted=1;d->ready=d->failed=0;
 while(!d->ready&&!d->failed){
  int rc=pthread_cond_timedwait(&d->cv,&d->mu,&deadline);
  if(rc!=0)break;
 }
 d->wanted=0;
 uint8_t*shot=d->shot;d->shot=NULL;
 unsigned w=d->shot_w,h=d->shot_h,frames=d->shot_frames;
 int ready=d->ready;
 pthread_mutex_unlock(&d->mu);
 if(!ready||!shot){free(shot);return NULL;}
 size_t n=(size_t)w*h;
 jintArray result=(*env)->NewIntArray(env,(jsize)(n+3));
 if(result){
  jint header[3]={(jint)w,(jint)h,(jint)frames};
  (*env)->SetIntArrayRegion(env,result,0,3,header);
  // Explicit RGBA -> Android ARGB; bounded chunks, no extra full-frame array.
  jint block[1024];
  for(size_t off=0;off<n;off+=1024){
   size_t len=n-off<1024?n-off:1024;
   for(size_t i=0;i<len;i++){
    uint8_t*p=shot+(off+i)*4;
    block[i]=(jint)(0xff000000u|((uint32_t)p[0]<<16)|((uint32_t)p[1]<<8)|p[2]);
   }
   (*env)->SetIntArrayRegion(env,result,(jsize)(off+3),(jsize)len,block);
   if((*env)->ExceptionCheck(env))break;
  }
 }
 free(shot);return result;
}
JNIEXPORT void JNICALL Java_com_vobich_bluestar_cloudprobe_C110NativeFrameDecoder_dispose(JNIEnv*env,jclass cls,jlong handle){
 (void)env;(void)cls;close_decoder((Decoder*)(intptr_t)handle);
}
