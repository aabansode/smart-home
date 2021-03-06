package ie.sheehan.smarthome.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import ie.sheehan.smarthome.R;
import ie.sheehan.smarthome.model.IntrusionReading;
import ie.sheehan.smarthome.utility.DateUtility;

public class IntrusionViewActivity extends AppCompatActivity {

    public final static int INTENT_SOURCE_SERVICE = 1;
    public final static int INTENT_SOURCE_ACTIVITY = 2;

    ImageView imageView;
    TextView dateView;
    TextView timeView;

    IntrusionReading intrusionReading;
    int intentSource = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intrusion_view);

        imageView = (ImageView) findViewById(R.id.image_intrusion);
        dateView = (TextView) findViewById(R.id.text_label_intrusion_date);
        timeView = (TextView) findViewById(R.id.text_label_intrusion_time);

        Bundle arguments = getIntent().getExtras();
        intrusionReading = (IntrusionReading) arguments.getSerializable("intrusion");
        intentSource = arguments.getInt("source");

        if (intrusionReading == null) {
            Toast.makeText(this, R.string.toast_no_intrusion_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        String dateText = DateUtility.getDateFormat().format(intrusionReading.getDate());
        dateView.setText(String.format(getResources().getString(R.string.text_label_intrusion_date), dateText));

        String timeText = DateUtility.getTimeFormat().format(intrusionReading.getDate());
        timeView.setText(String.format(getResources().getString(R.string.text_label_intrusion_time), timeText));

        imageView.setImageBitmap(intrusionReading.getImage());
        imageView.refreshDrawableState();
    }


    public void returnToIntrusionList(View view) {
        if (intentSource == INTENT_SOURCE_SERVICE) {
            startActivity(new Intent(this, IntrusionListActivity.class));
        }
        else if (intentSource == INTENT_SOURCE_ACTIVITY) {
            super.onBackPressed();
        }
        else {
            super.onBackPressed();
        }
    }

}
