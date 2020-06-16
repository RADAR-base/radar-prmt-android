package org.radarbase.garmin.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.garmin.health.GarminHealth;
import com.garmin.health.GarminHealthInitializationException;
import org.radarbase.garmin.R;

public class TestActivity  extends AppCompatActivity {


  @SuppressLint("NewApi")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d(this.getClass().getSimpleName(), "UI TEST:   onCreate()");

    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    try {
      GarminHealth.initialize(this, getString(R.string.companion_license));
    }
    catch(GarminHealthInitializationException e)
    {
      // Be sure to handle the exception. There are a number of things
      // that can go wrong in initialization.
      Toast.makeText(this,"fail",Toast.LENGTH_SHORT).show();
    }

    if(GarminHealth.isInitialized()) {
      Toast.makeText(this,"Initalized",Toast.LENGTH_SHORT).show();
    }
  }
}
