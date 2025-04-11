package com.example.bluetoothapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private static final UUID ESP32_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // UUID standard SPP

    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private BluetoothCallback callback;
    private int state;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // Rien ne se passe
    public static final int STATE_CONNECTING = 1; // Connexion en cours
    public static final int STATE_CONNECTED = 2;  // Connecté

    public interface BluetoothCallback {
        void onConnectionStateChanged(int state);
        void onDataReceived(float temperature, float voltage, int batteryPercentage, String timestamp, float brightness);
        void onConnectionFailed();
    }

    public BluetoothService(BluetoothCallback callback) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.state = STATE_NONE;
        this.handler = new Handler(Looper.getMainLooper());
        this.callback = callback;
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "Connexion à: " + device.getName());

        // Annuler les threads qui tentent d'établir une connexion
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Annuler tous les threads qui gèrent une connexion
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Démarrer le thread pour se connecter au périphérique
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "Connecté au périphérique");

        // Annuler le thread qui a terminé la connexion
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Annuler tous les threads actuellement en cours d'exécution
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Démarrer le thread pour gérer la connexion
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(STATE_CONNECTED);
    }

    public synchronized void stop() {
        Log.d(TAG, "Arrêt du service Bluetooth");

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_NONE);
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + this.state + " -> " + state);
        this.state = state;
        handler.post(() -> callback.onConnectionStateChanged(state));
    }

    public synchronized int getState() {
        return state;
    }

    /**
     * Thread qui tente d'établir une connexion sortante avec un périphérique
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(ESP32_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Erreur lors de la création du socket", e);
                handler.post(() -> callback.onConnectionFailed());
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "ConnectThread démarré");
            setName("ConnectThread");

            // Toujours annuler la découverte car elle ralentit une connexion
            bluetoothAdapter.cancelDiscovery();

            // Établir la connexion au BluetoothSocket
            try {
                mmSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Impossible de se connecter au socket", e);
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "Impossible de fermer le socket pendant l'échec de connexion", e2);
                }
                handler.post(() -> callback.onConnectionFailed());
                return;
            }

            // Réinitialiser le ConnectThread car nous avons terminé
            synchronized (BluetoothService.this) {
                connectThread = null;
            }

            // Démarrer le thread connecté
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Fermeture du socket Connect échouée", e);
            }
        }
    }

    /**
     * Thread qui gère une connexion établie
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread créé");
            mmSocket = socket;
            InputStream tmpIn = null;

            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Erreur lors de la création du flux d'entrée", e);
            }

            mmInStream = tmpIn;
        }

        public void run() {
            Log.i(TAG, "ConnectedThread démarré");
            BufferedReader reader = new BufferedReader(new InputStreamReader(mmInStream));
            
            while (true) {
                try {
                    if (!mmSocket.isConnected()) {
                        break;
                    }
                    
                    String line = reader.readLine();
                    if (line != null) {
                        Log.d(TAG, "Données reçues: " + line);
                        
                        // Analyser les données
                        processMessage(line);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Déconnecté", e);
                    setState(STATE_NONE);
                    handler.post(() -> callback.onConnectionFailed());
                    break;
                }
            }
        }

        // Traitement des données reçues
        private void processMessage(String message) {
            try {
                // Format attendu: "T:25.5,V:3.3,B:75,L:800,H:12:34:56" (Température, Tension, Pourcentage de batterie, Luminosité, Horodatage)
                Log.d(TAG, "Message reçu: " + message);
                if (message == null || message.isEmpty()) {
                    Log.w(TAG, "Message vide reçu");
                    return;
                }

                float temperature = 0;
                float voltage = 0;
                int batteryPercentage = 0;
                float brightness = 0;
                String timestamp = "--:--:--";

                // Découper la chaîne en parties
                String[] parts = message.split(",");
                if (parts.length < 3) {
                    Log.w(TAG, "Format de message invalide: " + message);
                    return;
                }

                for (String part : parts) {
                    String[] keyValue = part.split(":");
                    if (keyValue.length != 2) continue;

                    String key = keyValue[0];
                    String value = keyValue[1];

                    try {
                        if (key.equals("T")) {
                            temperature = Float.parseFloat(value);
                        } else if (key.equals("V")) {
                            voltage = Float.parseFloat(value);
                        } else if (key.equals("B")) {
                            batteryPercentage = Integer.parseInt(value);
                        } else if (key.equals("L")) {
                            brightness = Float.parseFloat(value);
                        } else if (key.equals("H")) {
                            timestamp = value;
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Erreur de parsing pour " + key + ":" + value, e);
                    }
                }

                // Envoyer les données à l'activité principale
                final float finalTemp = temperature;
                final float finalVoltage = voltage;
                final int finalBattery = batteryPercentage;
                final float finalBrightness = brightness;
                final String finalTimestamp = timestamp;

                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onDataReceived(finalTemp, finalVoltage, finalBattery, finalTimestamp, finalBrightness);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors du traitement du message", e);
            }
        }
        
        /**
         * Calcule le pourcentage de batterie basé sur la tension mesurée
         * @param measuredVoltage La tension mesurée après le diviseur de tension
         * @return Le pourcentage de batterie (0-100)
         */
        private int calculateBatteryPercentage(float measuredVoltage) {
            // Facteur du diviseur de tension (tension batterie réelle / tension mesurée)
            // Si la tension mesurée est 3.16V pour une batterie de 14V, le facteur est environ 4.43
            final float VOLTAGE_DIVIDER_FACTOR = 4.43f;
            
            // La tension réelle de la batterie
            float batteryVoltage = measuredVoltage * VOLTAGE_DIVIDER_FACTOR;
            
            // Plage de tension de la batterie
            final float BATTERY_MAX_VOLTAGE = 14.0f; // Batterie complètement chargée
            final float BATTERY_MIN_VOLTAGE = 11.0f; // Batterie déchargée
            
            // Calcul du pourcentage
            int percentage = Math.round((batteryVoltage - BATTERY_MIN_VOLTAGE) * 100 / (BATTERY_MAX_VOLTAGE - BATTERY_MIN_VOLTAGE));
            
            // Limiter le pourcentage entre 0 et 100
            return Math.max(0, Math.min(100, percentage));
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Fermeture du socket Connected échouée", e);
            }
        }
    }
} 