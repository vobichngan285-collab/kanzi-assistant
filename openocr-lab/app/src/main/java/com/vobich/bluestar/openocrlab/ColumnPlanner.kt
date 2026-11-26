package com.vobich.bluestar.openocrlab
import kotlin.math.*

/** Infer a recurring two-column gutter in deskewed coordinates, not from recognized words.
 * Only modify rows in a locally supported table block. Non-table rows stay intact. */
object ColumnPlanner {
 private fun median(xs:List<Float>):Float=xs.sorted()[xs.size/2]
 data class Gutter(val x:Float,val support:Int)
 fun complete(rows:List<List<RowPlanner.R>>):List<List<RowPlanner.R>>{
  if(rows.size<3)return rows
  val all=rows.flatten();if(all.isEmpty())return rows
  val h=median(all.map{it.h});if(h<=0)return rows
  // A recurring gutter is an x coordinate in the largest substantial gap of multiple rows.
  val gaps=rows.map{row->
   val s=row.sortedBy{it.l}
   s.zipWithNext().filter{(a,b)->b.l-a.r>=h*1.4f}.maxByOrNull{(a,b)->b.l-a.r}
   ?.let{(a,b)->a.r to b.l}
  }
  val candidates=gaps.filterNotNull().flatMap{(l,r)->listOf(l+(r-l)*.35f,(l+r)/2,l+(r-l)*.65f)}
  val best=candidates.maxWithOrNull(compareBy<Float>{x->gaps.count{g->g!=null&&x>g.first+h*.15f&&x<g.second-h*.15f}}.thenBy{x->x})?:return rows
  val votes=gaps.map{g->g!=null&&best>g.first+h*.15f&&best<g.second-h*.15f}
  if(votes.count{it}<3)return rows
  val supporting=rows.indices.filter{votes[it]}
  // Do not extrapolate to isolated headings, footers or different layouts.
  val block=rows.indices.filter{i->votes[i]&&supporting.any{j->j!=i&&abs(i-j)<=2}}
  if(block.size<3)return rows
  val lefts=block.map{i->rows[i].filter{it.r<=best}}
  val rights=block.map{i->rows[i].filter{it.l>=best}}
  val leftStart=median(lefts.filter{it.isNotEmpty()}.map{a->a.minOf{it.l}})
  val leftEnd=lefts.filter{it.isNotEmpty()}.map{a->a.maxOf{it.r}}.sorted().let{it[(it.size-1)*3/4]}
  return rows.mapIndexed{i,row->
   if(row.size<2||i !in block||row.any{it.l<best&&it.r>best})return@mapIndexed row
   val left=row.filter{it.r<=best};val right=row.filter{it.l>=best}
   if(left.isEmpty()||right.size!=1)return@mapIndexed row
   // Require local support for this right column's location; avoid merging unrelated groups.
   val rr=right.single()
   val nearby=block.filter{it!=i&&abs(it-i)<=3}.mapNotNull{j->rights[block.indexOf(j)].singleOrNull()}
   if(nearby.count{abs(it.r-rr.r)<=h*2f||abs(it.l-rr.l)<=h*2f}<2)return@mapIndexed row
   val l=left.reduce{a,b->a.union(b)}
   // Bridge all interior holes, and recover a bounded amount at the leading/trailing edge.
   val start=min(l.l,max(l.l-h*.9f,leftStart))
   val end=max(l.r,min(best-h*.12f,min(l.r+h*.9f,leftEnd)))
   if(end<=start||(end-start)/l.h>19f)return@mapIndexed row
   listOf(RowPlanner.R(start,l.t,end,l.b,l.score),rr)
  }
 }
}
