package com.onlytuner.tuner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.icu.text.DecimalFormat;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends AppCompatActivity {
    HorizontalScrollView noteLayout;
    TextView currentHz, targetHz, targetNote;
    DecimalFormat df2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //create the launcher that will handle the response for a permission request
        ActivityResultLauncher<String> requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (!isGranted)
                        Toast.makeText(getApplicationContext(), "Need to accept permission for app to work correctly.", Toast.LENGTH_SHORT).show();
                    else {
                        realOnCreate();
                    }
                });

        //check if permission is already obtained
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
            realOnCreate();
        } else { //ask the user to grant permission
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO);
        }
    }

    public void realOnCreate() {
        //get local resources from xml into memory
        String[] tuningValues = getResources().getStringArray(R.array.tuningVals);
        String[] tuningNames = getResources().getStringArray(R.array.tunings);

        // Disable Scrolling for HorizontalScrollView by setting up an OnTouchListener to do nothing
        noteLayout = findViewById(R.id.notes);
        noteLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });


        //inits
        df2 = new DecimalFormat();
        df2.setMaximumFractionDigits(2);
        df2.setMinimumFractionDigits(2);
        final double[] lastXNotes = new double[1000];
        final double[] lastXAvgs = new double[1000];
        //init array
        for (int i = 0; i < lastXNotes.length; i++) {
            lastXNotes[i] = 20.6;
        }
        for (int i = 0; i < lastXAvgs.length; i++) {
            lastXAvgs[i] = 0;
        }

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);

        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult res, AudioEvent e) {
                final float pitchInHz = res.getPitch();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if (pitchInHz != -1.0) {
                            float curHzTotal = 0;
                            float avgHz = 0f;
                            int countZero = 0;

                            //add new hz to the array
                            for (int i = 0; i < lastXNotes.length - 1; i++) {
                                lastXNotes[i + 1] = lastXNotes[i];
                                curHzTotal += lastXNotes[i + 1];
                                if (lastXNotes[i + 1] == 0)
                                    countZero++;
                            }
                            lastXNotes[0] = pitchInHz;
                            curHzTotal += pitchInHz;

                            //get avg
                            avgHz = curHzTotal / (lastXNotes.length - countZero);
                            //add new avgHz to the array
                            for (int i = 0; i < lastXAvgs.length - 1; i++) {
                                lastXAvgs[i + 1] = lastXAvgs[i];
                                curHzTotal += lastXAvgs[i + 1];
                            }
                            lastXAvgs[0] = avgHz;

                            if (isAvgMoving())
                                scrollToNote(avgHz);
                        }
                    }

                    //returns whether there is a correlation between previous avgs
                    public boolean isAvgMoving() {
                        double difference = lastXAvgs[1] - lastXAvgs[0];
                        int direction = 0;

                        //difference >= 0 means second is bigger than first, direction = 1
                        //else second is smaller than first , direction = -1

                        if (difference >= 0) {
                            direction = 1;
                        } else {
                            direction = -1;
                        }
                        for (int i = 2; i < lastXAvgs.length; i++) {
                            difference = lastXAvgs[i] - lastXAvgs[i - 1];
                            if (direction == 1) {
                                if (difference >= 0) {
                                } else
                                    return false;
                            } else {
                                if (difference >= 0)
                                    return false;
                            }
                        }
                        return true;
                    }
                });
            }
        };
        AudioProcessor pitchProcessor = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
        dispatcher.addAudioProcessor(pitchProcessor);

        Thread audioThread = new Thread(dispatcher, "Audio Thread");
        audioThread.start();

        currentHz = findViewById(R.id.currentHz);
        targetHz = findViewById(R.id.targetHz);
        targetNote = findViewById(R.id.targetNote);

        //create objects
        TextView tuning = findViewById(R.id.tuning);
        Spinner spinner = findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                tuning.setText(tuningValues[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, tuningNames));

        //button for debugging
            /*EditText editText = findViewById(R.id.input);
            findViewById(R.id.materialButton).setOnClickListener(v -> {
                String input = editText.getText().toString().replaceAll("[^0-9.]", "");
                if (input.length() > 0) {
                    scrollToNote(Double.parseDouble(input));
                }
            });*/

    }

    public void scrollToNote(double hz) {
        //set current hz
        currentHz.setText(df2.format(hz) + " Hz");

        //calculate where to scroll to
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int offset = displayMetrics.widthPixels / 2;

        double notesAway = 12 * (Math.log(hz / 16.35) / Math.log(2));

        setCurrentHzValues(notesAway);

        //dp = dp from C0 + dp of a letter * notesAway in one octave
        double dp = 700 + 200 * (notesAway % 12);
        int px = (int) (dp * displayMetrics.density);
        noteLayout.smoothScrollTo(px - offset, 0);
    }


    public void setCurrentHzValues(double notesAway) {
        double betweenNotesAway = notesAway - ((int) notesAway);

        //set the target notes int
        int targetNoteInt;
        if (betweenNotesAway > .5)
            targetNoteInt = ((int) notesAway) + 1;
        else
            targetNoteInt = ((int) notesAway);

        //find target Hz based on equation
        double targetHzDouble = 16.35 * Math.pow(2, (double) targetNoteInt / 12);

        //find target Note based on target int
        String targetHzString;
        switch (targetNoteInt % 12) {
            default:
                targetHzString = "E";
                break;
            case 0:
                targetHzString = "C";
                break;
            case 1:
                targetHzString = "D\u266D";
                break;
            case 2:
                targetHzString = "D";
                break;
            case 3:
                targetHzString = "E\u266D";
                break;
            case 4:
                targetHzString = "E";
                break;
            case 5:
                targetHzString = "F";
                break;
            case 6:
                targetHzString = "G\u266D";
                break;
            case 7:
                targetHzString = "G";
                break;
            case 8:
                targetHzString = "A\u266D";
                break;
            case 9:
                targetHzString = "A";
                break;
            case 10:
                targetHzString = "B\u266D";
                break;
            case 11:
                targetHzString = "B";
                break;
        }

        //set targetHz
        targetHz.setText(df2.format(targetHzDouble) + " Hz");
        targetNote.setText(targetHzString);
    }
}