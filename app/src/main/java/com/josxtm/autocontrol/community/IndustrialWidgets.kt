package com.josxtm.autocontrol.community

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Local vector geometry, never font glyphs or remotely loaded illustrations. */
@Composable fun TechIcon(kind:String,modifier:Modifier=Modifier.size(23.dp),color:Color=LocalContentColor.current,description:String?=null){
    Canvas(modifier.then(if(description==null)Modifier else Modifier.semantics { contentDescription=description })){
        val u=size.width/24f;val stroke=androidx.compose.ui.graphics.drawscope.Stroke(1.7f*u)
        fun line(x:Float,y:Float,a:Float,b:Float)=drawLine(color,androidx.compose.ui.geometry.Offset(x*u,y*u),androidx.compose.ui.geometry.Offset(a*u,b*u),1.7f*u)
        fun box(x:Float,y:Float,w:Float,h:Float)=drawRect(color,androidx.compose.ui.geometry.Offset(x*u,y*u),androidx.compose.ui.geometry.Size(w*u,h*u),style=stroke)
        when(kind){
            "home"->{val p=Path().apply{moveTo(2*u,11*u);lineTo(12*u,2*u);lineTo(22*u,11*u)};drawPath(p,color,style=stroke);box(5f,11f,14f,11f);box(10f,15f,4f,7f)}
            "network","ct"->{box(8f,1f,8f,6f);line(12f,7f,12f,11f);line(4f,11f,20f,11f);line(4f,11f,4f,16f);line(20f,11f,20f,16f);box(1f,16f,6f,6f);box(17f,16f,6f,6f);box(9f,16f,6f,6f);line(12f,11f,12f,16f)}
            "terminal"->{box(1f,3f,22f,18f);line(5f,8f,9f,12f);line(9f,12f,5f,16f);line(12f,16f,18f,16f)}
            "search"->{drawCircle(color,7*u,androidx.compose.ui.geometry.Offset(9*u,9*u),style=stroke);line(14f,14f,22f,22f)}
            "settings"->{drawCircle(color,8*u,center,style=stroke);drawCircle(color,3*u,center,style=stroke);for(i in 0..7){val a=i*Math.PI/4;line((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+11*kotlin.math.cos(a)).toFloat(),(12+11*kotlin.math.sin(a)).toFloat())}}
            "database"->{drawOval(color,androidx.compose.ui.geometry.Offset(2*u,2*u),androidx.compose.ui.geometry.Size(20*u,7*u),style=stroke);line(2f,5f,2f,20f);line(22f,5f,22f,20f);drawArc(color,0f,180f,false,androidx.compose.ui.geometry.Offset(2*u,16*u),androidx.compose.ui.geometry.Size(20*u,7*u),style=stroke)}
            "play"->{drawPath(Path().apply{moveTo(6*u,3*u);lineTo(21*u,12*u);lineTo(6*u,21*u);close()},color)}
            "clear","cancel"->{line(5f,5f,19f,19f);line(19f,5f,5f,19f)}
            "expand"->{line(2f,8f,2f,2f);line(2f,2f,8f,2f);line(16f,22f,22f,22f);line(22f,22f,22f,16f);line(2f,2f,10f,10f);line(22f,22f,14f,14f)}
            "status"->{drawCircle(color,8*u,center,style=stroke);line(7f,12f,10f,16f);line(10f,16f,17f,8f)}
            "lock"->{box(4f,10f,16f,12f);drawArc(color,180f,180f,false,androidx.compose.ui.geometry.Offset(7*u,1*u),androidx.compose.ui.geometry.Size(10*u,16*u),style=stroke)}
            "menu"->{for(y in listOf(5f,12f,19f))drawCircle(color,1.7f*u,androidx.compose.ui.geometry.Offset(12*u,y*u))}
            "bell"->{drawArc(color,180f,180f,false,androidx.compose.ui.geometry.Offset(5*u,3*u),androidx.compose.ui.geometry.Size(14*u,17*u),style=stroke);line(5f,11f,5f,17f);line(19f,11f,19f,17f);line(3f,18f,21f,18f);drawCircle(color,2*u,androidx.compose.ui.geometry.Offset(12*u,22*u))}
            else->{box(4f,2f,16f,20f);line(7f,7f,17f,7f);line(7f,12f,17f,12f);line(7f,17f,15f,17f)}
        }
    }
}
@Composable fun IndustrialPanel(title:String,icon:String,modifier:Modifier=Modifier,actions:@Composable RowScope.()->Unit={},content:@Composable ColumnScope.()->Unit){
    Surface(modifier,shape=RoundedCornerShape(5.dp),color=MaterialTheme.colorScheme.surfaceVariant,border=BorderStroke(1.dp,Color(0xFF92979B))){Column(Modifier.padding(4.dp)){
        Row(Modifier.fillMaxWidth().height(32.dp).background(Brush.verticalGradient(listOf(Color(0xFF62666A),Color(0xFF363B40))),RoundedCornerShape(3.dp)).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            TechIcon(icon,color=Color.White);Spacer(Modifier.width(8.dp));Text(title,Modifier.weight(1f),color=Color.White,fontSize=14.sp,maxLines=1);actions()
        };content()
    }}
}
@Composable fun IndustrialButton(label:String,icon:String="",enabled:Boolean=true,primary:Boolean=false,onClick:()->Unit){
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(4.dp),border=BorderStroke(1.dp,Color(0xFF969B9F)),modifier=Modifier.heightIn(min=36.dp),color=if(primary)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface){
        Row(Modifier.background(Brush.verticalGradient(if(primary)listOf(Color(0xFFC63640),Color(0xFF960E1B))else listOf(MaterialTheme.colorScheme.surface,MaterialTheme.colorScheme.surfaceVariant))).padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            val color=if(primary)Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .45f)
            if(icon.isNotEmpty())TechIcon(icon,Modifier.size(19.dp),color);Text(label,fontSize=12.sp,color=color,maxLines=1)
        }
    }
}
