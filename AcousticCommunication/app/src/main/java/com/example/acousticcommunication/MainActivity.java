package com.example.acousticcommunication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.example.acousticcommunication.Demodulate.Decode;
import static com.example.acousticcommunication.Global.BufferSize;
import static com.example.acousticcommunication.Global.Channel;
import static com.example.acousticcommunication.Global.Encoding;
import static com.example.acousticcommunication.Global.NoiseFileName;
import static com.example.acousticcommunication.Global.OutputFileName;
import static com.example.acousticcommunication.Global.RawFileName;
import static com.example.acousticcommunication.Global.RecordFileName;
import static com.example.acousticcommunication.Global.SamplingRate;
import static com.example.acousticcommunication.Global.ReadWaveFile;
import static com.example.acousticcommunication.Global.GenerateAudioFile;
import static com.example.acousticcommunication.Global.WriteWaveFileHeader;

@SuppressLint("SetTextI18n")
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)

public class MainActivity extends AppCompatActivity {
    Button StartRecordButton;
    Button StopRecordButton;
    Button DecodeRecordButton;
    Button PlayAudioButton;
    Button PlayNoiseButton;
    Button MakeAudioButton;
    Button ConfirmButton;
    Button GEN_DATAButton;
    TextView ShowDataTextView;
    // EditText StorageEditText;
    EditText DataEditText;
    CanvasView PaintCanvasView;
    MediaPlayer mediaPlayer;

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            DecodeRecordButton.setEnabled(true);
            ShowMessage("Write wave file finished.");
            return false;
        }
    });

    String directory = null;
    boolean Recording = false;

    String x_text = "";
    double[] T;

    private MediaPlayer.OnCompletionListener onCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer m) {
            if (mediaPlayer == null)
                return;
            mediaPlayer.release();
            mediaPlayer = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        init();

        //StatusTextView.setText("STOPPED");
        StopRecordButton.setEnabled(false);
        //DecodeRecordButton.setEnabled(false);
        DecodeRecordButton.setEnabled(true);
        StartRecordButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                if (directory == null) {
                    ShowMessage("Please set the storage directory first.");
                    return;
                }
                StopRecordButton.setEnabled(true);
                StartRecordButton.setEnabled(false);
                //StatusTextView.setText("RUNNING");
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        StartRecord(directory);
                        WriteWaveFile(directory + RecordFileName, directory);
                    }
                });
                thread.start();
            }
        });
        StopRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Recording = false;
                StopRecordButton.setEnabled(false);
                StartRecordButton.setEnabled(true);
                //StatusTextView.setText("STOPPED");
            }
        });
        DecodeRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (directory == null) {
                    ShowMessage("Please set the storage directory first.");
                    return;
                }
                File file = new File(directory + RecordFileName);
                if (!file.exists()) {
                    ShowMessage("Audio file not found.");
                    return;
                }
                try {
                    double[] signal = ReadWaveFile(directory + RecordFileName);
//                    PaintCanvasView.signal = signal;
//                    PaintCanvasView.color = Color.RED;
//                    PaintCanvasView.invalidate();
                    Math_main MM = new Math_main();
                    String str = Demodulate.Decode(signal);
                    MM.cal();
                    RealMatrix x = new Array2DRowRealMatrix(MM.N + 10, 1);
                    for (int i = 0; i < str.length(); i ++ )
                    {
                        if (str.charAt(i) == '0') x.setEntry(i, 0, 0);
                        else if(str.charAt(i) == '1') x.setEntry(i, 0, 1);
                        else x.setEntry(i, 0, 2);
                    }

                    MM.get_y(x);
                    RealMatrix u_d = MM.pdecode(MM.y_bob);
                    String plain_text = "";
                    for (int i = 0; i < MM.K; i ++ )
                    {
                        int tmp = (int) u_d.getEntry(i, 0);
                        plain_text += tmp;
                    }
                    ShowMessage("Decoded data \"" + plain_text + "\"");
                    DecodeRecordButton.setEnabled(false);
                } catch (IOException e) {
                    Log.e("AcousticCommunication", "read record file failed");
                }
            }
        });
        MakeAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = DataEditText.getText().toString();
                ShowConfirmDialog("Confirmation", "Confirm to encode data \"" + text + "\"?");
            }
        });
        PlayAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (directory == null) {
                    ShowMessage("Please set the storage directory first.");
                    return;
                }
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(directory + OutputFileName);
                    mediaPlayer.setOnCompletionListener(onCompletionListener);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException e) {
                    Log.e("AcousticCommunication", "failed to load audio data source");
                }
            }
        });
        PlayNoiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (directory == null) {
                    ShowMessage("Please set the storage directory first.");
                    return;
                }
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(directory + NoiseFileName);
                    mediaPlayer.setOnCompletionListener(onCompletionListener);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException e) {
                    Log.e("AcousticCommunication", "failed to load noise data source");
                }
            }
        });
        ConfirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String path = "/storage/emulated/0/OFDMRecorder";
                if (path.charAt(path.length() - 1) != '/')
                    path += '/';
                File file = new File(path);
                if (!file.exists()) {
                    ShowMessage("Invalid directory name.");
                    return;
                }
                directory = path;
                ShowMessage("Directory successfully set to: " + directory);
            }
        });
        GEN_DATAButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view)
            {
                Math_main MM = new Math_main();
                MM.cal();
                MM.get_my_u();
                RealMatrix x = MM.pencode();


                for (int i = 0; i < MM.N; i ++ )
                {
                    int tmp = (int) x.getEntry(i, 0);
                    x_text += tmp;
                }

                double[] signal = Modulate.Encode(x_text);
                //ShowSignalOnCanvas(signal);
                GenerateAudioFile(signal, directory);
                String plaintext = "GEN_DATA = ";
                for (int i = 1; i <= MM.K; i ++ )
                {
                    int tmp = (int) MM.u.getEntry(i, 0);
                    plaintext += tmp;
                }

                ShowDataTextView.setText(plaintext);
            }
        });
        mediaPlayer = new MediaPlayer();
    }

    void StartRecord(String path) {
        File file = new File(path + RawFileName);
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            Log.e("AcousticCommunication", "failed to create file " + file.toString());
        }
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SamplingRate, Channel, Encoding, BufferSize);
            byte[] buffer = new byte[BufferSize];
            audioRecord.startRecording();
            Recording = true;
            while (Recording) {
                int length = audioRecord.read(buffer, 0, BufferSize);
                for (int i = 0; i < length; i++)
                    dataOutputStream.write(buffer[i]);
            }
            audioRecord.stop();
            dataOutputStream.flush();
            dataOutputStream.close();
        } catch (Throwable t) {
            Log.e("AcousticCommunication", "record failed");
        }
    }

    private void WriteWaveFile(String name, String path) {
        File file = new File(path + RecordFileName);
        if (file.exists())
            file.delete();
        int channels = 1;
        long byteRate = 16 * SamplingRate * channels / 8;
        byte[] data = new byte[BufferSize];
        try {
            file.createNewFile();
            FileInputStream fileInputStream = new FileInputStream(path + RawFileName);
            FileOutputStream fileOutputStream = new FileOutputStream(name);
            long audioLength = fileInputStream.getChannel().size();
            long dataLength = audioLength + 36;
            WriteWaveFileHeader(fileOutputStream, audioLength, dataLength, (long) SamplingRate, channels, byteRate);
            while (fileInputStream.read(data) != -1)
                fileOutputStream.write(data);
            fileInputStream.close();
            fileOutputStream.flush();
            fileOutputStream.close();
            handler.sendEmptyMessage(0);
        } catch (FileNotFoundException e) {
            Log.e("AcousticCommunication", "audio file not found");
        } catch (IOException e) {
            Log.e("AcousticCommunication", "write wave file failed");
        }
    }


//    private void ShowSignalOnCanvas(double[] signal) {
//        PaintCanvasView.signal = signal;
//        PaintCanvasView.invalidate();
//    }

    private void ShowConfirmDialog(String title, String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(title);
        dialog.setIcon(R.mipmap.ic_launcher_round);
        dialog.setMessage(message);
        dialog.setPositiveButton("Yes"
                , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (directory == null) {
                            ShowMessage("Please set the storage directory first.");
                            dialog.dismiss();
                            return;
                        }
//                        String text = DataEditText.getText().toString();
//                        if (text.length() == 0) {
//                            ShowMessage("Please don't send empty message.");
//                            dialog.dismiss();
//                            return;
//                        }
                        dialog.dismiss();
                        double[] signal = Modulate.Encode(x_text);
                        // PaintCanvasView.color = Color.BLUE;
                        //ShowSignalOnCanvas(signal);
                        GenerateAudioFile(signal, directory);
                    }
                });
        dialog.setNegativeButton("No"
                , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DataEditText.setText("");
                        dialog.dismiss();
                    }
                });
        dialog.create().show();
    }

    private void ShowMessage(String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Notice");
        dialog.setIcon(R.mipmap.ic_launcher_round);
        dialog.setMessage(message);
        dialog.setNegativeButton("OK"
                , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        dialog.create().show();
    }

    void init() {

        setContentView(R.layout.activity_main);
        GetPermission();


        StartRecordButton = findViewById(R.id.StartButton);
        StopRecordButton = findViewById(R.id.FinishButton);
        PlayAudioButton = findViewById(R.id.PlayAudioButton);
        PlayNoiseButton = findViewById(R.id.PlayNoiseButton);
        MakeAudioButton = findViewById(R.id.MakeAudioButton);
        DecodeRecordButton = findViewById(R.id.DecodeButton);
        ConfirmButton = findViewById(R.id.ConfirmButton);
        GEN_DATAButton = findViewById(R.id.GEN_DATA);
        ShowDataTextView = findViewById(R.id.ShowDataTextView);
        //StorageEditText = findViewById(R.id.StorageEditText);
        DataEditText = findViewById(R.id.DataEditText);
        // PaintCanvasView = findViewById(R.id.PaintCanvasView);
    }

    private void GetPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        }, 0);
    }
}