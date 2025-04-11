package com.example.bluetoothapp;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private List<BluetoothDevice> devices = new ArrayList<>();
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    public DeviceAdapter(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void setDevices(List<BluetoothDevice> devices) {
        this.devices = devices;
        notifyDataSetChanged();
    }

    public void addDevice(BluetoothDevice device) {
        // Vérifier si l'appareil n'est pas déjà dans la liste
        for (BluetoothDevice existingDevice : devices) {
            if (existingDevice.getAddress().equals(device.getAddress())) {
                return;
            }
        }
        
        devices.add(device);
        notifyDataSetChanged();
    }

    public void clearDevices() {
        devices.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        String deviceName = device.getName();
        
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "Appareil inconnu";
        }
        
        holder.deviceNameTextView.setText(deviceName);
        holder.deviceAddressTextView.setText(device.getAddress());
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView;
        TextView deviceAddressTextView;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.deviceNameTextView);
            deviceAddressTextView = itemView.findViewById(R.id.deviceAddressTextView);
        }
    }
} 