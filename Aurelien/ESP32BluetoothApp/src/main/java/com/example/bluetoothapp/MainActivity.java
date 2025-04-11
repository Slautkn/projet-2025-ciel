package com.example.bluetoothapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements BluetoothService.BluetoothCallback {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final int MAX_DATA_POINTS = 50; // Nombre maximum de points sur le graphique

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothService bluetoothService;
    private DeviceAdapter deviceAdapter;
    private AlertDialog deviceDialog;
    
    private TextView deviceStatusTextView;
    private Button scanButton;
    private Button connectButton;
    private TextView temperatureValueTextView;
    private TextView voltageValueTextView;
    private TextView batteryPercentageTextView;
    private TextView timestampValueTextView;
    private TextView brightnessValueTextView;
    private LineChart temperatureChart;
    private LineChart voltageChart;
    private LineChart brightnessChart;
    
    private final List<Entry> temperatureEntries = new ArrayList<>();
    private final List<Entry> voltageEntries = new ArrayList<>();
    private final List<Entry> brightnessEntries = new ArrayList<>();
    private int xIndex = 0;
    
    private BluetoothDevice selectedDevice;
    
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Permission BLUETOOTH_CONNECT non accordée");
                        return;
                    }
                    
                    BluetoothDevice device = null;
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                        } else {
                            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        }
                        
                        if (device != null && device.getName() != null) {
                            deviceAdapter.addDevice(device);
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException lors de l'accès au périphérique", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur dans bluetoothReceiver.onReceive", e);
            }
        }
    };

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        setupBluetooth();
                    } else {
                        Toast.makeText(this, "Bluetooth est nécessaire pour cette application", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erreur dans enableBluetoothLauncher", e);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Masquer la barre de titre pour éviter qu'elle ne cache des éléments
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // Gestionnaire d'exceptions global pour éviter un crash complet
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "ERREUR NON GÉRÉE : " + throwable.getMessage(), throwable);
            // Ne pas terminer l'application - laisser Android gérer l'erreur
        });
        
        try {
            Log.d(TAG, "Démarrage de onCreate");
            setContentView(R.layout.activity_main);
            
            // Initialiser les vues
            try {
                initializeViews();
                setupCharts();
                setupButtonListeners();
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'initialisation des vues", e);
                Toast.makeText(this, "Erreur d'initialisation de l'interface", Toast.LENGTH_LONG).show();
            }
            
            // Initialiser l'adaptateur Bluetooth
            try {
                initializeBluetooth();
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'initialisation du Bluetooth", e);
                Toast.makeText(this, "Erreur d'initialisation Bluetooth", Toast.LENGTH_LONG).show();
            }
            
            Log.d(TAG, "Fin de onCreate avec succès");
        } catch (Exception e) {
            Log.e(TAG, "Erreur critique dans onCreate", e);
            Toast.makeText(this, "Erreur critique au démarrage", Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeViews() {
        Log.d(TAG, "Initialisation des vues");
        deviceStatusTextView = findViewById(R.id.deviceStatusTextView);
        scanButton = findViewById(R.id.scanButton);
        connectButton = findViewById(R.id.connectButton);
        temperatureValueTextView = findViewById(R.id.temperatureValueTextView);
        voltageValueTextView = findViewById(R.id.voltageValueTextView);
        batteryPercentageTextView = findViewById(R.id.batteryPercentageTextView);
        timestampValueTextView = findViewById(R.id.timestampValueTextView);
        brightnessValueTextView = findViewById(R.id.brightnessValueTextView);
        temperatureChart = findViewById(R.id.temperatureChart);
        voltageChart = findViewById(R.id.voltageChart);
        brightnessChart = findViewById(R.id.brightnessChart);
        Log.d(TAG, "Vues initialisées avec succès");
    }
    
    private void setupButtonListeners() {
        Log.d(TAG, "Configuration des écouteurs de boutons");
        scanButton.setOnClickListener(v -> showDeviceListDialog());
        connectButton.setOnClickListener(v -> connectToDevice());
        Log.d(TAG, "Écouteurs de boutons configurés");
    }
    
    private void initializeBluetooth() {
        Log.d(TAG, "Initialisation du Bluetooth");
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            Log.d(TAG, "BluetoothAdapter obtenu: " + (bluetoothAdapter != null));
        } else {
            Log.e(TAG, "Impossible d'obtenir BluetoothManager");
        }
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter non disponible");
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Vérifier et demander les permissions
        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Log.d(TAG, "onResume - Enregistrement du récepteur Bluetooth");
            // Enregistrer pour les découvertes d'appareils Bluetooth
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(bluetoothReceiver, filter);
            
            // Initialiser le service Bluetooth s'il n'est pas déjà initialisé
            if (bluetoothService == null) {
                bluetoothService = new BluetoothService(this);
                Log.d(TAG, "BluetoothService initialisé");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans onResume", e);
        }
    }

    @Override
    protected void onPause() {
        try {
            Log.d(TAG, "onPause - Désenregistrement du récepteur");
            // Arrêter la découverte Bluetooth
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            
            // Désenregistrer le récepteur
            try {
                unregisterReceiver(bluetoothReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Le récepteur n'était pas enregistré", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans onPause", e);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            Log.d(TAG, "onDestroy - Nettoyage des ressources");
            // Arrêter le service Bluetooth
            if (bluetoothService != null) {
                bluetoothService.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans onDestroy", e);
        }
        super.onDestroy();
    }

    private void checkPermissions() {
        try {
            Log.d(TAG, "Vérification des permissions, SDK: " + Build.VERSION.SDK_INT);
            // Pour Android 13 (API 33) et plus récent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                String[] permissions = {
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                };
                
                boolean allGranted = true;
                for (String permission : permissions) {
                    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        Log.w(TAG, "Permission non accordée: " + permission);
                        break;
                    }
                }
                
                if (!allGranted) {
                    Log.d(TAG, "Demande de permissions pour Android 13+");
                    ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
                    return;
                }
            } 
            // Pour Android 12 (API 31 et 32)
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                String[] permissions = {
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                };
                
                boolean allGranted = true;
                for (String permission : permissions) {
                    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        Log.w(TAG, "Permission non accordée: " + permission);
                        break;
                    }
                }
                
                if (!allGranted) {
                    Log.d(TAG, "Demande de permissions pour Android 12");
                    ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
                    return;
                }
            } else {
                // Android 11 (API 30) et plus ancien
                String[] permissions = {
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                };
                
                boolean allGranted = true;
                for (String permission : permissions) {
                    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        Log.w(TAG, "Permission non accordée: " + permission);
                        break;
                    }
                }
                
                if (!allGranted) {
                    Log.d(TAG, "Demande de permissions pour Android 11 et moins");
                    ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
                    return;
                }
            }
            
            // Si toutes les permissions sont accordées, continuer à configurer Bluetooth
            Log.d(TAG, "Toutes les permissions sont accordées");
            setupBluetooth();
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans checkPermissions", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        try {
            if (requestCode == REQUEST_PERMISSIONS) {
                boolean allGranted = true;
                
                for (int i = 0; i < permissions.length; i++) {
                    Log.d(TAG, "Permission " + permissions[i] + ": " + 
                          (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
                    
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                    }
                }
                
                if (allGranted) {
                    Log.d(TAG, "Toutes les permissions accordées après demande");
                    setupBluetooth();
                } else {
                    Log.w(TAG, "Certaines permissions ont été refusées");
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans onRequestPermissionsResult", e);
        }
    }

    private void setupBluetooth() {
        try {
            Log.d(TAG, "Configuration du Bluetooth");
            if (bluetoothAdapter == null) {
                Log.e(TAG, "BluetoothAdapter est null");
                Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            Log.d(TAG, "Bluetooth activé? " + bluetoothAdapter.isEnabled());
            if (!bluetoothAdapter.isEnabled()) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    try {
                        Log.d(TAG, "Lancement de la demande d'activation Bluetooth");
                        enableBluetoothLauncher.launch(enableBtIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Erreur lors de la demande d'activation Bluetooth", e);
                        Toast.makeText(this, "Veuillez activer le Bluetooth manuellement", Toast.LENGTH_LONG).show();
                    }
                } else {
                    // Demande de permission nécessaire
                    Log.d(TAG, "Demande de permission BLUETOOTH_CONNECT");
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 
                        REQUEST_PERMISSIONS);
                }
            } else {
                Log.d(TAG, "Bluetooth déjà activé");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans setupBluetooth", e);
        }
    }

    private void setupCharts() {
        // Configuration du graphique de température
        setupChart(temperatureChart, "Température (°C)", Color.RED, 0, 50);
        
        // Configuration du graphique de tension
        setupChart(voltageChart, "Tension (V)", Color.BLUE, 0, 15);
        
        // Configuration du graphique de luminosité
        setupChart(brightnessChart, "Luminosité (lux)", Color.YELLOW, 0, 1000);
    }

    private void setupChart(LineChart chart, String label, int color, float minY, float maxY) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        
        // Configurer l'axe X
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf(Math.round(value));
            }
        });
        
        // Configurer l'axe Y
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(minY);
        leftAxis.setAxisMaximum(maxY);
        
        // Désactiver l'axe Y droit
        chart.getAxisRight().setEnabled(false);
        
        // Préparer un jeu de données vide
        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), label);
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate();
    }

    private void updateChart(LineChart chart, List<Entry> entries, float newValue) {
        LineData data = chart.getData();
        
        if (data != null) {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
            
            if (set == null) {
                return;
            }
            
            // Ajouter les nouvelles données
            entries.add(new Entry(xIndex, newValue));
            
            // Limiter le nombre de points affichés
            if (entries.size() > MAX_DATA_POINTS) {
                entries.remove(0);
                
                // Ajuster les valeurs X pour les entrées restantes
                for (int i = 0; i < entries.size(); i++) {
                    Entry e = entries.get(i);
                    entries.set(i, new Entry(i, e.getY()));
                }
            }
            
            set.setValues(entries);
            data.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.setVisibleXRangeMaximum(MAX_DATA_POINTS);
            chart.moveViewToX(entries.size() - 1);
            chart.invalidate();
        }
    }

    private void showDeviceListDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_device_list, null);
            builder.setView(dialogView);
            
            RecyclerView deviceRecyclerView = dialogView.findViewById(R.id.deviceRecyclerView);
            Button scanAgainButton = dialogView.findViewById(R.id.scanAgainButton);
            
            deviceAdapter = new DeviceAdapter(device -> {
                selectedDevice = device;
                deviceDialog.dismiss();
                connectButton.setEnabled(true);
                deviceStatusTextView.setText(String.format("Appareil sélectionné: %s", device.getName()));
            });
            
            deviceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            deviceRecyclerView.setAdapter(deviceAdapter);
            
            scanAgainButton.setOnClickListener(v -> startDiscovery());
            
            deviceDialog = builder.create();
            deviceDialog.show();
            
            startDiscovery();
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'affichage de la liste des appareils", e);
            Toast.makeText(this, "Erreur d'affichage de la liste", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startDiscovery() {
        try {
            Log.d(TAG, "Démarrage de la découverte Bluetooth");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission BLUETOOTH_SCAN non accordée, demande en cours");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_PERMISSIONS);
                return;
            }
            
            deviceAdapter.clearDevices();
            
            try {
                if (bluetoothAdapter.isDiscovering()) {
                    Log.d(TAG, "Annulation de la découverte en cours");
                    bluetoothAdapter.cancelDiscovery();
                }
                
                // Démarrer la découverte
                Log.d(TAG, "Tentative de démarrage de la découverte");
                boolean started = bluetoothAdapter.startDiscovery();
                if (!started) {
                    Log.e(TAG, "Impossible de démarrer la découverte Bluetooth");
                    Toast.makeText(this, "Impossible de démarrer la recherche d'appareils", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "Découverte Bluetooth démarrée avec succès");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException lors de la découverte", e);
                Toast.makeText(this, "Problème d'autorisation Bluetooth", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans startDiscovery", e);
            Toast.makeText(this, "Erreur lors de la recherche", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void connectToDevice() {
        try {
            if (selectedDevice == null) {
                Log.w(TAG, "Tentative de connexion sans appareil sélectionné");
                return;
            }
            
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission BLUETOOTH_CONNECT non accordée pour la connexion");
                return;
            }
            
            // Arrêter la découverte car elle ralentit la connexion
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            
            // Connexion à l'appareil
            Log.d(TAG, "Connexion à l'appareil: " + selectedDevice.getName());
            bluetoothService.connect(selectedDevice);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la connexion à l'appareil", e);
            Toast.makeText(this, "Erreur de connexion", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Implémentations de BluetoothCallback
    
    @Override
    public void onConnectionStateChanged(int state) {
        try {
            Log.d(TAG, "État de connexion changé: " + state);
            switch (state) {
                case BluetoothService.STATE_NONE:
                    deviceStatusTextView.setText(R.string.no_device);
                    connectButton.setText(R.string.connect);
                    break;
                case BluetoothService.STATE_CONNECTING:
                    deviceStatusTextView.setText("Connexion en cours...");
                    break;
                case BluetoothService.STATE_CONNECTED:
                    if (selectedDevice != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        deviceStatusTextView.setText(getString(R.string.connected_to, selectedDevice.getName()));
                        connectButton.setText(R.string.disconnect);
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans onConnectionStateChanged", e);
        }
    }
    
    @Override
    public void onDataReceived(float temperature, float voltage, int batteryPercentage, String timestamp, float brightness) {
        xIndex++;
        Log.d(TAG, "Données reçues - Temp: " + temperature + "°C, Tension: " + voltage + "V, Batterie: " + batteryPercentage + "%, Luminosité: " + brightness + " lux, Heure: " + timestamp);

        temperatureValueTextView.setText(String.format("%.1f °C", temperature));
        voltageValueTextView.setText(String.format("%.2f V", voltage));
        batteryPercentageTextView.setText(String.format("%d%%", batteryPercentage));
        
        // Affichage de l'horodatage provenant de l'ESP32
        timestampValueTextView.setText(timestamp);
        
        // Affichage de la luminosité provenant de l'ESP32
        brightnessValueTextView.setText(String.format("%.0f lux", brightness));

        updateChart(temperatureChart, temperatureEntries, temperature);
        updateChart(voltageChart, voltageEntries, voltage);
        updateChart(brightnessChart, brightnessEntries, brightness);
    }
    
    @Override
    public void onConnectionFailed() {
        try {
            Log.e(TAG, "Échec de la connexion Bluetooth");
            Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_SHORT).show();
            deviceStatusTextView.setText(R.string.no_device);
            connectButton.setText(R.string.connect);
        } catch (Exception e) {
            Log.e(TAG, "Erreur dans onConnectionFailed", e);
        }
    }
} 