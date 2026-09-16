package com.bookwormbliss.app.ui.common
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.bookwormbliss.app.R
object Ui {
    fun dp(c:Context,n:Int)= (n*c.resources.displayMetrics.density).toInt()
    fun text(c:Context,s:String,size:Number=14f,bold:Boolean=false)=TextView(c).apply{text=s;textSize=size.toFloat();setTextColor(Color.rgb(90,70,80));if(bold)setTypeface(typeface,1);setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4))}
    fun button(c:Context,s:String,onClick:()->Unit)=Button(c).apply{text=s;textSize=12f;setTextColor(Color.WHITE);setBackgroundColor(c.getColor(R.color.app_primary));setOnClickListener{onClick()};minHeight=dp(c,44)}
    fun card(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(c,16).toFloat();setStroke(dp(c,1),c.getColor(R.color.app_divider))}}
    fun row(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    fun divider(c:Context)=View(c).apply{setBackgroundColor(c.getColor(R.color.app_divider));layoutParams=ViewGroup.LayoutParams(-1,dp(c,1))}
}
