package ie.sheehan.smarthome.fragment;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.joda.time.Period;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ie.sheehan.smarthome.R;
import ie.sheehan.smarthome.activity.TemperatureChartActivity;
import ie.sheehan.smarthome.dialog.DatePickerFragment;
import ie.sheehan.smarthome.model.EnvironmentReading;
import ie.sheehan.smarthome.model.HeatingStatus;
import ie.sheehan.smarthome.utility.DateUtility;
import ie.sheehan.smarthome.utility.HttpRequestHandler;

import static ie.sheehan.smarthome.fragment.SettingsFragment.VALUE_METRIC_TEMPERATURE_CELSIUS;
import static ie.sheehan.smarthome.fragment.SettingsFragment.VALUE_METRIC_TEMPERATURE_FAHRENHEIT;
import static ie.sheehan.smarthome.fragment.SettingsFragment.getPreferredTemperatureMetric;
import static ie.sheehan.smarthome.utility.DateUtility.getDateFormat;

public class EnvironmentFragment extends Fragment {

    static final long INITIAL_DELAY = 500;

    long period = 1000;


    // ============================================================================================
    // DECLARING CLASS VARIABLES
    // ============================================================================================
    Resources res;
    SharedPreferences preferences;

    ScheduledExecutorService executorService;

    Date fromDate;
    Date toDate;

    ToggleButton toggleHeating;

    TextView temperatureView;
    TextView humidityView;

    TextView heatingLastOn;
    TextView heatingDuration;

    TextView fromDateView;
    TextView toDateView;


    /**
     * Default constructor.
     */
    public EnvironmentFragment() {}


    // ============================================================================================
    // FRAGMENT LIFECYCLE METHODS
    // ============================================================================================
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_environment, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        res = getResources();
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        period = SettingsFragment.getPreferredConnectionPeriod();

        setInitialDateValues();

        toggleHeating = (ToggleButton) getActivity().findViewById(R.id.switch_heating);

        temperatureView = (TextView) getActivity().findViewById(R.id.text_temperature);
        humidityView = (TextView) getActivity().findViewById(R.id.text_humidity);

        heatingLastOn = (TextView) getActivity().findViewById(R.id.text_label_heating_last_on);
        heatingDuration = (TextView) getActivity().findViewById(R.id.text_label_heating_duration);

        fromDateView = (TextView) getActivity().findViewById(R.id.text_from_date);
        toDateView = (TextView) getActivity().findViewById(R.id.text_to_date);

        fromDateView.setText(res.getString(R.string.text_from_date, getDateFormat().format(fromDate)));
        toDateView.setText(res.getString(R.string.text_to_date, getDateFormat().format(toDate)));

        executorService = Executors.newScheduledThreadPool(10);

        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                new GetTemperature().execute();
            }
        }, INITIAL_DELAY, period, TimeUnit.MILLISECONDS);

        new GetHeatingStatus().execute();

        toggleHeating.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                new ToggleHeating().execute(isChecked);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        executorService.shutdown();
    }

    @Override
    public void onResume() {
        super.onResume();

        new GetHeatingStatus().execute();

        if (executorService.isShutdown()) {
            executorService = Executors.newScheduledThreadPool(10);

            executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    new GetTemperature().execute();
                }
            }, INITIAL_DELAY, period, TimeUnit.MILLISECONDS);
        }
    }


    // ============================================================================================
    // PRIVATE METHODS
    // ============================================================================================
    private void setInitialDateValues() {
        Calendar fromDateCal = Calendar.getInstance();
        fromDateCal.set(Calendar.HOUR, 0);
        fromDateCal.set(Calendar.MINUTE, 0);
        fromDateCal.set(Calendar.SECOND, 0);

        Calendar toDateCal = Calendar.getInstance();
        toDateCal.set(Calendar.HOUR, 23);
        toDateCal.set(Calendar.MINUTE, 59);
        toDateCal.set(Calendar.SECOND, 59);

        fromDate = fromDateCal.getTime();
        toDate = toDateCal.getTime();
    }


    // ============================================================================================
    // BUTTON LISTENER METHODS
    // ============================================================================================
    /**
     * Launches a {@link TemperatureChartActivity} to display a bar chart of {@link EnvironmentReading} values
     * between two specified dates.
     */
    public void openChart(){
        if (fromDate == null || toDate == null || fromDate.after(toDate)){
            Toast.makeText(getActivity(), R.string.toast_invalid_date_range, Toast.LENGTH_SHORT).show();
            return;
        }

        new GetTemperatureInRange().execute(fromDate, toDate);
    }

    /**
     * Opens a new {@link DatePickerFragment} dialog to select a date and set the value of
     * {@link EnvironmentFragment#fromDate} to the result.
     */
    public void openSetFromDateDialog(){
        DatePickerFragment fragment = new DatePickerFragment();

        Bundle arguments = new Bundle();
        arguments.putSerializable("date", fromDate);
        fragment.setArguments(arguments);

        fragment.addOnDateSetListener(new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, dayOfMonth, 0, 0, 0);
                fromDate = calendar.getTime();

                fromDateView.setText(res.getString(R.string.text_from_date, getDateFormat().format(fromDate)));
            }
        });

        fragment.show(getActivity().getSupportFragmentManager(), "fromDatePicker");
    }

    /**
     * Opens a new {@link DatePickerFragment} dialog to select a date and set the value of
     * {@link EnvironmentFragment#toDate} to the result.
     */
    public void openSetToDateDialog(){
        DatePickerFragment fragment = new DatePickerFragment();

        Bundle arguments = new Bundle();
        arguments.putSerializable("date", toDate);
        fragment.setArguments(arguments);

        fragment.addOnDateSetListener(new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, dayOfMonth, 23, 59, 59);
                toDate = calendar.getTime();

                toDateView.setText(res.getString(R.string.text_to_date, getDateFormat().format(toDate)));
            }
        });

        fragment.show(getActivity().getSupportFragmentManager(), "toDatePicker");
    }


    // ============================================================================================
    // INNER CLASS DECLARATION
    // ============================================================================================
    /**
     * An {@link AsyncTask} that executes a HTTP request for the latest {@link EnvironmentReading}.
     */
    private class GetTemperature extends AsyncTask<Void, Void, EnvironmentReading> {
        @Override
        protected EnvironmentReading doInBackground(Void... params) {
            return HttpRequestHandler.getInstance().getEnvironmentReading();
        }

        @Override
        protected void onPostExecute(EnvironmentReading envReading) {
            if (getPreferredTemperatureMetric() == VALUE_METRIC_TEMPERATURE_CELSIUS) {
                temperatureView.setText(res.getString(R.string.text_temperature_display_celsius, envReading.getTemperature()));
            }
            else if (getPreferredTemperatureMetric() == VALUE_METRIC_TEMPERATURE_FAHRENHEIT){
                temperatureView.setText(res.getString(R.string.text_temperature_display_fahrenheit, envReading.getTemperatureInFahrenheit()));
            }

            humidityView.setText(res.getString(R.string.text_humidity_display, envReading.getHumidity()));
        }
    }

    /**
     * An {@link AsyncTask} that executes a HTTP request for a list of {@link EnvironmentReading}
     * values that were logged within a range of dates.
     */
    private class GetTemperatureInRange extends AsyncTask<Date, Void, List<EnvironmentReading>> {
        @Override
        protected List<EnvironmentReading> doInBackground(Date... range) {
            Date from = range[0];
            Date to = range[1];

            return HttpRequestHandler.getInstance().getEnvironmentReadingsInRange(from, to);
        }

        @Override
        protected void onPostExecute(List<EnvironmentReading> environmentReadings) {
            super.onPostExecute(environmentReadings);

            if (environmentReadings.isEmpty()) {
                Toast.makeText(getContext(), R.string.toast_no_readings_found, Toast.LENGTH_SHORT).show();
                return;
            }

            Bundle arguments = new Bundle();
            arguments.putSerializable("envReadings", (ArrayList) environmentReadings);
            Intent intent = new Intent(getActivity(), TemperatureChartActivity.class);
            intent.putExtras(arguments);
            getActivity().startActivity(intent);
        }
    }

    private class GetHeatingStatus extends AsyncTask<Void, Void, HeatingStatus> {
        @Override
        protected HeatingStatus doInBackground(Void... params) {
            return HttpRequestHandler.getInstance().getHeatingStatus();
        }

        @Override
        protected void onPostExecute(HeatingStatus heatingStatus) {
            super.onPostExecute(heatingStatus);

            String heatingLastOnText = res.getString(R.string.text_heating_last_on);
            String heatingDurationText = res.getString(R.string.text_heating_duration);

            if (heatingStatus != null) {
                heatingLastOn.setText(String.format(heatingLastOnText,
                        DateUtility.getShortDateFormat().format(heatingStatus.getLastOn()),
                        DateUtility.getShortTimeFormat().format(heatingStatus.getLastOn())));

                Period period = heatingStatus.getDuration().toPeriod();
                heatingDuration.setText(String.format(heatingDurationText, DateUtility.getPeriodFormat().print(period)));

                toggleHeating.setChecked(heatingStatus.isOn());
            }
        }
    }

    private class ToggleHeating extends AsyncTask<Boolean, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Boolean... params) {
            boolean on = params[0];
            return HttpRequestHandler.getInstance().toggleHeating(on);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            if (aBoolean) {
                try {
                    Thread.sleep(1500);
                    new GetHeatingStatus().execute();
                } catch (InterruptedException e) {
                    Log.e("ERROR", e.toString());
                }
            }
            else {
                Toast.makeText(getActivity(), "Failure", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
