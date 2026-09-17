package com.bookwormbliss.app.core.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Book(
    val id:String, var title:String, var author:String, var coverPath:String?, var description:String?, var publisher:String?, var language:String?, var year:Int?, var identifier:String?,
    var progress:Float=0f, var spineIndex:Int=0, var pageIndex:Int=0, var pageCount:Int=1, var isFavorite:Boolean=false, var isCurrentlyReading:Boolean=false,
    var addedDate:Long=System.currentTimeMillis(), var lastOpened:Long=0L, var sourceFilename:String="", var extractedRoot:String="", var checksum:String=""
)
class BookStore(context:Context) {
    private val prefs=context.getSharedPreferences("bookworm_library",Context.MODE_PRIVATE); private val key="books"
    fun all():MutableList<Book>{ val a=JSONArray(prefs.getString(key,"[]")); val out=mutableListOf<Book>(); for(i in 0 until a.length()) out+=from(a.getJSONObject(i)); return out }
    fun save(book:Book){val list=all();val idx=list.indexOfFirst{it.id==book.id};if(idx>=0)list[idx]=book else list.add(0,book);write(list)}
    fun delete(id:String){write(all().filter{it.id!=id})}; fun get(id:String)=all().firstOrNull{it.id==id}
    fun findByChecksum(checksum:String)=all().firstOrNull{checksum.isNotBlank()&&it.checksum==checksum}
    fun findByIdentity(identifier:String?,title:String,author:String)=all().firstOrNull{
        if(!identifier.isNullOrBlank() && !it.identifier.isNullOrBlank()) it.identifier.equals(identifier,true)
        else it.title.trim().equals(title.trim(),true) && it.author.trim().equals(author.trim(),true)
    }
    private fun write(list:List<Book>){prefs.edit().putString(key,JSONArray().also{a->list.forEach{a.put(to(it))}}.toString()).apply()}
    private fun to(b:Book)=JSONObject().apply{put("id",b.id);put("title",b.title);put("author",b.author);put("cover",b.coverPath);put("description",b.description);put("publisher",b.publisher);put("language",b.language);put("year",b.year);put("identifier",b.identifier);put("progress",b.progress);put("spine",b.spineIndex);put("page",b.pageIndex);put("pageCount",b.pageCount);put("favorite",b.isFavorite);put("reading",b.isCurrentlyReading);put("added",b.addedDate);put("last",b.lastOpened);put("source",b.sourceFilename);put("root",b.extractedRoot);put("checksum",b.checksum)}
    private fun from(o:JSONObject)=Book(o.getString("id"),o.optString("title","Untitled"),o.optString("author","Unknown Author"),o.optString("cover",null),o.optString("description",null),o.optString("publisher",null),o.optString("language",null),o.optInt("year",0).takeIf{it!=0},o.optString("identifier",null),o.optDouble("progress",0.0).toFloat(),o.optInt("spine",0),o.optInt("page",0),o.optInt("pageCount",1),o.optBoolean("favorite"),o.optBoolean("reading"),o.optLong("added",System.currentTimeMillis()),o.optLong("last",0),o.optString("source",""),o.optString("root","") ,o.optString("checksum",""))
}
