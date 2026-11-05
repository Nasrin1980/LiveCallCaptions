package com.beni.livecallcaptions;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MainActivity extends Activity {
    private Spinner direction;
    private EditText phone;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36,42,36,30);
        root.setBackgroundColor(Color.rgb(246,249,255));
        ScrollView scroll=new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        TextView title=new TextView(this);
        title.setText("Beni Live Call Captions");
        title.setTextSize(26);
        title.setTypeface(null,Typeface.BOLD);
        title.setTextColor(Color.rgb(23,45,94));
        root.addView(title);
        TextView description=new TextView(this);
        description.setText("\nLive speech captions + translation (experimental).\nCellular call audio may not be available to this app. Try speakerphone, but results vary by device.\n");
        description.setTextSize(16);
        root.addView(description);
        direction=new Spinner(this);
        String[] options={"English → فارسی","فارسی → English"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        direction.setAdapter(adapter);
        root.addView(direction);
        Button overlay=new Button(this);
        overlay.setText("1. Allow floating caption window");
        root.addView(overlay);
        overlay.setOnClickListener(v->{
            if(!Settings.canDrawOverlays(this)){
                Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
                startActivity(i);
            } else new AlertDialog.Builder(this).setMessage("Floating window permission already granted.").setPositiveButton("OK",null).show();
        });
        Button start=new Button(this);
        start.setText("2. Start captions");
        root.addView(start);
        start.setOnClickListener(v->startCaptions());
        Button stop=new Button(this);
        stop.setText("Stop captions");
        root.addView(stop);
        stop.setOnClickListener(v->stopService(new Intent(this,CaptionService.class)));
        phone=new EditText(this);
        phone.setHint("Phone number");
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        root.addView(phone);
        Button dial=new Button(this);
        dial.setText("3. Open SIM dialer");
        root.addView(dial);
        dial.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone.getText().toString(),""));
            startActivity(intent);
        });
        TextView info=new TextView(this);
        info.setText("\nPrivacy: Captions are not saved. Speech recognition depends on the phone's speech service; ML Kit translation models download initially.\n\nUse only with the other person's consent.");
        root.addView(info);
    }
    private void startCaptions(){
        if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setMessage("First grant floating-window permission.").setPositiveButton("OK",null).show();return;}
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},13); return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},14);return;
        }
        Intent i=new Intent(this,CaptionService.class);
        i.putExtra("direction",direction.getSelectedItemPosition());
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) startCaptions();
    }
}
