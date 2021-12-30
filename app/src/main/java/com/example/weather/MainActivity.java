package com.example.weather;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    String TAG="nb";
    TextView id;
    TextView locate;
    TextView t1;
    TextView t2;
    TextView t3;
    TextView t4;
    TextView t5;
    private DB helper;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //
        id=(TextView) findViewById(R.id.cityid);
        locate=(TextView) findViewById(R.id.locate);
        t4=(TextView) findViewById(R.id.day);
        t5=(TextView) findViewById(R.id.time);
        t1=(TextView) findViewById(R.id.degree);
        t2=(TextView) findViewById(R.id.wet);
        t3=(TextView) findViewById(R.id.pm25);
        //
        helper = new DB(this);
        db=helper.getReadableDatabase();
        //天气sdk权限获取
        //读入城市csv
        //city();
    }

    public void click(View v){
        String nowId= id.getText().toString();
        if(!isCity(nowId))return;
        Cursor cursor = db.query("log", null, "id=?", new String[]{nowId}, null, null, null);
        if(cursor.moveToFirst()){
            Log.d("","读取记录");
            t4.setText(cursor.getString(1));
            t5.setText(cursor.getString(2));
            t1.setText(cursor.getString(3));
            t2.setText(cursor.getString(4));
            t3.setText(cursor.getString(5));
            cursor = db.query("city", new String[]{"id,province,city"}, "id=?", new String[]{nowId}, null, null, null);
            if(cursor.moveToFirst()){
                String province=cursor.getString(2)+"-"+cursor.getString(1);
                locate.setText(province);
            }
        }else{
            fresh(v);
            Log.d("","刷新记录");
        }

    }//有历史记录则直接读取

    public void fresh(View v){
        String nowId= id.getText().toString();
        if(!isCity(nowId))return;
        Cursor cursor = db.query("city", new String[]{"id,province,city"}, "id=?", new String[]{nowId}, null, null, null);
        if(cursor.moveToFirst()){
                String province=cursor.getString(2)+"-"+cursor.getString(1);
                locate.setText(province);
        }
        DownloadTask dl=new DownloadTask();
        dl.execute();
    }//有无历史记录均读取天气

    public void city(){
        InputStreamReader is = null;
        try {
            is = new InputStreamReader(getAssets().open("China-City-List-latest.csv"));
            BufferedReader reader = new BufferedReader(is);
            reader.readLine();//读取每行
            String line;
            while ((line = reader.readLine()) != null) {
                String temp[]=line.split(",");
                //Log.d("id",temp[0]);
                //Log.d("province",temp[2]);
                //Log.d("city",temp[7]);
                ContentValues cValue = new ContentValues();
                cValue.put("id",temp[0]);
                cValue.put("province",temp[2]);
                cValue.put("city",temp[7]);
                db.insert("city",null,cValue);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }//第一次启动程序时运行，读取城市信息

    public static String[] date(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日-HH:mm:ss");
        Date date = new Date();
        String[] str = sdf.format(date).split("-");
        return str;
    }//返回时间的字符串

    public void save(){
        String nowId= id.getText().toString();
        ContentValues cValue = new ContentValues();
        cValue.put("id",nowId);
        cValue.put("day",t4.getText().toString());
        cValue.put("time",t5.getText().toString());
        cValue.put("degree",t1.getText().toString());
        cValue.put("wet",t2.getText().toString());
        cValue.put("pm",t3.getText().toString());

        Cursor c=db.query("log", new String[]{"id"}, "id=?", new String[]{nowId}, null, null, null);
        if(c.moveToFirst()){
            db.update("log",cValue,"id="+nowId,null);
            Log.d("数据库操作：","已存在");
        }else{
            db.insert("log",null,cValue);
            Log.d("数据库操作：","未存在");
        }
    }//把历史记录存入数据库

    public boolean isCity(String cityId){
        if(cityId.length()!=9){
            Toast.makeText(getApplicationContext(), "城市代码位数非九位", Toast.LENGTH_SHORT).show();
            return false;
        }
        Cursor cursor = db.query("city", new String[]{"id,province,city"}, "id=?", new String[]{cityId}, null, null, null);
        if(cursor.moveToFirst()){
            return true;
        }
        Toast.makeText(getApplicationContext(), "无对应代码城市", Toast.LENGTH_SHORT).show();
        return false;
    }//验证代码够不够九位（九位代码指代一座城市），

    public String myString(String in){

        JsonObject dt = new JsonParser().parse(in).getAsJsonObject();
        JsonObject now=dt.get("now").getAsJsonObject();
        String js=now.get("temp").getAsString()+"-"+now.get("humidity").getAsString()+"-"+now.get("vis").getAsString();
        return js;
    }
    private StringBuilder sb = new StringBuilder("");
    private StringBuilder sbmsg = new StringBuilder("");
    private InputStream is = null;
    private BufferedReader br = null;
    private String msg = "",Location="101010200",WebKey="3e86db7fff424f62bc41781e363411c7";
    //异步请求类，WebKey是web api key值，location可以是地名也可以是经纬度等等。。。
    private class DownloadTask extends AsyncTask<Void, Integer, Boolean> {
        String url = "https://devapi.qweather.com/v7/weather/now?key=" + WebKey + "&location=" + Location;

        @Override
        protected Boolean doInBackground(Void... count) {
            try {
                URL uri = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) uri.openConnection();
                connection.setRequestMethod("GET");     //GET方式请求数据
                connection.setReadTimeout(5000);
                connection.setConnectTimeout(5000);
                connection.connect();   //开启连接
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    is = connection.getInputStream();
                    //参数字符串，如果拼接在请求链接之后，需要对中文进行 URLEncode   字符集 UTF-8
                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String line;
                    while ((line = br.readLine()) != null) {    //缓冲逐行读取
                        sb.append(line);
                    }
                    String jmsg = sb.toString();        //接收结果，接收到的是JSON格式的数据
                    msg = myString(jmsg);     //解析接收到的和风now天气json数据
                } else {
                    msg = "获取天气失败";
                }
            } catch (Exception ignored) {
                msg = "获取天气异常";
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            try {
                if (is != null) {
                    is.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (Exception ignored) {}
            if (result) {
                Log.d("",msg);
                String[]s=msg.split("-");
                t1.setText(s[0]);
                t2.setText(s[1]);
                t3.setText(s[2]);
                String[] day=date();
                t4.setText(day[0]);
                t5.setText(day[1]);
                save();
            } else {
                Log.d("","获取天气失败");
            }
        }

    }
}
