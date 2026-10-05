package com.royalmatch.overlay;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class OverlayService extends Service {
    private WindowManager wm; private LinearLayout panel; private WindowManager.LayoutParams lp;
    private float downX,downY; private int startX,startY; private boolean expanded=false;
    private static final String[][] ITEMS={{"NORMAL","NORMAL"},{"BLUE","BLUE"},{"GREEN","GREEN"},{"RED","RED"},{"YELLOW","YELLOW"},{"PINK","PINK"},{"ORANGE","ORANGE"},{"PROPELLER","PROPELLER"},{"V ROCKET","VROCKET"},{"H ROCKET","HROCKET"},{"TNT","TNT"},{"LIGHT BALL","LIGHTBALL"}};
    @Override public void onCreate(){ super.onCreate(); wm=(WindowManager)getSystemService(WINDOW_SERVICE); panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); Button main=new Button(this); main.setText("RM"); panel.addView(main); final LinearLayout menu=new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL); menu.setVisibility(View.GONE); panel.addView(menu); for(String[] item:ITEMS){ Button b=new Button(this); b.setText(item[0]); b.setOnClickListener(v->{writeCommand(item[1]);menu.setVisibility(View.GONE);expanded=false;}); menu.addView(b);} main.setOnClickListener(v->{expanded=!expanded;menu.setVisibility(expanded?View.VISIBLE:View.GONE);}); int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE; lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT); lp.gravity=Gravity.TOP|Gravity.START; lp.x=20; lp.y=180; main.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=lp.x;startY=lp.y;return false;case MotionEvent.ACTION_MOVE:if(Math.abs(e.getRawX()-downX)>8||Math.abs(e.getRawY()-downY)>8){lp.x=startX+(int)(e.getRawX()-downX);lp.y=startY+(int)(e.getRawY()-downY);wm.updateViewLayout(panel,lp);return true;}}return false;}}); wm.addView(panel,lp); }
    private void writeCommand(String cmd){ try{ if(Build.VERSION.SDK_INT>=29){ Uri c=MediaStore.Downloads.EXTERNAL_CONTENT_URI; getContentResolver().delete(c,MediaStore.MediaColumns.DISPLAY_NAME+"=? AND "+MediaStore.MediaColumns.RELATIVE_PATH+"=?",new String[]{"RoyalMatchSpawn.command","Download/"}); ContentValues cv=new ContentValues(); cv.put(MediaStore.MediaColumns.DISPLAY_NAME,"RoyalMatchSpawn.command"); cv.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain"); cv.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/"); Uri u=getContentResolver().insert(c,cv); if(u==null)throw new Exception("MediaStore insert failed"); try(OutputStream os=getContentResolver().openOutputStream(u,"w")){os.write((cmd+"\n").getBytes(StandardCharsets.UTF_8));}} else {java.io.File f=new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),"RoyalMatchSpawn.command");try(java.io.FileOutputStream os=new java.io.FileOutputStream(f,false)){os.write((cmd+"\n").getBytes(StandardCharsets.UTF_8));}} Toast.makeText(this,cmd,Toast.LENGTH_SHORT).show(); }catch(Exception e){Toast.makeText(this,"Command failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    @Override public void onDestroy(){if(panel!=null&&wm!=null)wm.removeView(panel);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
