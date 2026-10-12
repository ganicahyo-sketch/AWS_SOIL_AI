package id.turus.stasiuncuaca;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final String PREFS = "thingspeak_config";
    private static final String DEFAULT_CHANNEL = "2981880";
    private static final String DEFAULT_READ_KEY = "P4B56Z7HZM56Q7HJ";
    private static final String DEFAULT_AI_KEY = "";
    private android.content.SharedPreferences p;
    private EditText title, channel, readKey, aiKey, aiModel, crop, lat, lon, elev;
    private Spinner cult, weatherProvider;
    private EditText weatherApiKey;
    private static final int GPS_REQ = 78;
    private TextView toggleRead, toggleAi;
    private EditText[] fieldName = new EditText[8];
    private EditText[] fieldUnit = new EditText[8];

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        title=findViewById(R.id.appTitle); channel=findViewById(R.id.channel); readKey=findViewById(R.id.readKey);
        aiKey=findViewById(R.id.aiKey); aiModel=findViewById(R.id.aiModel); crop=findViewById(R.id.crop);
        lat=findViewById(R.id.latitude); lon=findViewById(R.id.longitude); elev=findViewById(R.id.elevation);
        cult=findViewById(R.id.cultivation); weatherProvider=findViewById(R.id.weatherProvider); weatherApiKey=findViewById(R.id.weatherApiKey); toggleRead=findViewById(R.id.toggleReadKey); toggleAi=findViewById(R.id.toggleAiKey);
        for(int i=0;i<8;i++){
            int n=i+1;
            fieldName[i]=findViewById(getResources().getIdentifier("fieldName"+n,"id",getPackageName()));
            fieldUnit[i]=findViewById(getResources().getIdentifier("fieldUnit"+n,"id",getPackageName()));
        }
        ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Konvensional / PHT","Organik"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); cult.setAdapter(ad);
        ArrayAdapter<String> weatherAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Open-Meteo (disarankan, tanpa API key)","WeatherAPI.com (perlu API key)"});
        weatherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); weatherProvider.setAdapter(weatherAdapter);
        load();
        findViewById(R.id.useGps).setOnClickListener(v->requestGps());
        findViewById(R.id.saveSettings).setOnClickListener(v->save());
        findViewById(R.id.cancelSettings).setOnClickListener(v->finish());
        toggleRead.setOnClickListener(v->toggle(readKey,toggleRead));
        toggleAi.setOnClickListener(v->toggle(aiKey,toggleAi));
    }

    private void load(){
        title.setText(p.getString("app_title","STASIUN CUACA"));
        String savedChannel=p.getString("channel","").trim();
        String savedReadKey=p.getString("read_key","").trim();
        channel.setText(savedChannel.isEmpty()?DEFAULT_CHANNEL:savedChannel);
        readKey.setText(savedReadKey.isEmpty()?DEFAULT_READ_KEY:savedReadKey);
        String savedAiKey=p.getString("ai_api_key","").trim();
        aiKey.setText(savedAiKey.isEmpty()?DEFAULT_AI_KEY:savedAiKey);
        aiModel.setText(p.getString("ai_model","gpt-6-luna"));
        crop.setText(p.getString("crop","Tanaman pertanian"));
        lat.setText(f(p.getFloat("latitude",Float.NaN))); lon.setText(f(p.getFloat("longitude",Float.NaN))); elev.setText(f(p.getFloat("elevation",Float.NaN)));
        cult.setSelection(p.getString("farm_cultivation_mode","Konvensional / PHT").toLowerCase(Locale.US).contains("organik")?1:0);
        weatherProvider.setSelection("WeatherAPI.com".equals(p.getString("weather_provider","Open-Meteo"))?1:0);
        weatherApiKey.setText(p.getString("weatherapi_key", ""));
        for(int i=0;i<8;i++){
            fieldName[i].setText(p.getString("field_name_"+(i+1),""));
            fieldUnit[i].setText(p.getString("field_unit_"+(i+1),""));
        }
    }

    private String f(float x){return Float.isFinite(x)?String.format(Locale.US,"%.6f",x):"";}

    private void save(){
        String tv=title.getText().toString().trim(); if(tv.isEmpty())tv="STASIUN CUACA";
        String cv=crop.getText().toString().trim(); if(cv.isEmpty())cv="Tanaman pertanian";
        float la=numOptional(lat.getText().toString()), lo=numOptional(lon.getText().toString()), el=numOptional(elev.getText().toString());
        if(!Float.isNaN(la)&&(la<-90||la>90)){lat.setError("Latitude -90..90");return;}
        if(!Float.isNaN(lo)&&(lo<-180||lo>180)){lon.setError("Longitude -180..180");return;}
        android.content.SharedPreferences.Editor e=p.edit().putString("app_title",tv).putString("channel",channel.getText().toString().trim().isEmpty()?DEFAULT_CHANNEL:channel.getText().toString().trim())
                .putString("read_key",readKey.getText().toString().trim().isEmpty()?DEFAULT_READ_KEY:readKey.getText().toString().trim())
                .putString("ai_api_key",aiKey.getText().toString().trim()).putString("ai_model",aiModel.getText().toString().trim().isEmpty()?"gpt-6-luna":aiModel.getText().toString().trim())
                .putString("crop",cv).putString("farm_cultivation_mode",cult.getSelectedItem().toString())
                .putString("weather_provider",weatherProvider.getSelectedItemPosition()==1?"WeatherAPI.com":"Open-Meteo")
                .putString("weatherapi_key",weatherApiKey.getText().toString().trim());
        if(!Float.isNaN(la))e.putFloat("latitude",la); else e.remove("latitude");
        if(!Float.isNaN(lo))e.putFloat("longitude",lo); else e.remove("longitude");
        if(!Float.isNaN(el))e.putFloat("elevation",el); else e.remove("elevation");
        for(int i=0;i<8;i++){
            e.putString("field_name_"+(i+1),fieldName[i].getText().toString().trim());
            e.putString("field_unit_"+(i+1),fieldUnit[i].getText().toString().trim());
        }
        e.apply(); Toast.makeText(this,"Pengaturan tersimpan.",Toast.LENGTH_SHORT).show(); finish();
    }


    private void requestGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},GPS_REQ);return;}
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            String provider=lm.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:null);
            if(provider==null){Toast.makeText(this,"Aktifkan Lokasi/GPS HP lalu coba lagi.",Toast.LENGTH_LONG).show();return;}
            Toast.makeText(this,"Meminta koordinat GPS terbaru...",Toast.LENGTH_SHORT).show();
            LocationListener listener=new LocationListener(){
                @Override public void onLocationChanged(Location loc){lm.removeUpdates(this);lat.setText(String.format(Locale.US,"%.6f",loc.getLatitude()));lon.setText(String.format(Locale.US,"%.6f",loc.getLongitude()));p.edit().putFloat("latitude",(float)loc.getLatitude()).putFloat("longitude",(float)loc.getLongitude()).apply();Toast.makeText(SettingsActivity.this,"Koordinat baru terisi dan tersimpan. Tekan Simpan untuk mempertahankan pengaturan lainnya.",Toast.LENGTH_LONG).show();}
                @Override public void onProviderDisabled(String x){} @Override public void onProviderEnabled(String x){} @Override public void onStatusChanged(String x,int y,android.os.Bundle z){}
            };
            lm.requestSingleUpdate(provider,listener,Looper.getMainLooper());
        }catch(Exception e){Toast.makeText(this,"GPS gagal: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==GPS_REQ){for(int g:grantResults)if(g==PackageManager.PERMISSION_GRANTED){requestGps();return;}Toast.makeText(this,"Izin lokasi diperlukan untuk mengambil GPS.",Toast.LENGTH_LONG).show();}}

    private float numOptional(String s){try{String t=s==null?"":s.trim();return t.isEmpty()?Float.NaN:Float.parseFloat(t.replace(',','.'));}catch(Exception ex){return Float.NaN;}}
    private void toggle(EditText e,TextView t){boolean visible=e.getInputType()==(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);e.setInputType(InputType.TYPE_CLASS_TEXT|(visible?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));t.setText(visible?"TAMPILKAN":"SEMBUNYIKAN");e.setSelection(e.length());}
}
