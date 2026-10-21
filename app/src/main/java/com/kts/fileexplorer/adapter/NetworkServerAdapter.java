package com.kts.fileexplorer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kts.fileexplorer.R;
import com.kts.fileexplorer.network.NetworkServerInfo;

import java.util.ArrayList;
import java.util.List;

public class NetworkServerAdapter extends RecyclerView.Adapter<NetworkServerAdapter.ViewHolder> {

    public interface OnServerClickListener {
        void onServerClick(NetworkServerInfo server);
        void onServerMenuClick(NetworkServerInfo server, View anchor);
    }

    private final List<NetworkServerInfo> servers = new ArrayList<>();
    private final OnServerClickListener listener;

    public NetworkServerAdapter(OnServerClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<NetworkServerInfo> list) {
        servers.clear();
        if (list != null) {
            servers.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network_server, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NetworkServerInfo server = servers.get(position);
        holder.tvName.setText(server.getName());

        String details = server.getType() + " • " + server.getHost() + ":" + server.getPort();
        if (server.getShareName() != null && !server.getShareName().isEmpty()) {
            details += " (" + server.getShareName() + ")";
        }
        holder.tvDetails.setText(details);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onServerClick(server);
        });

        holder.btnMenu.setOnClickListener(v -> {
            if (listener != null) listener.onServerMenuClick(server, v);
        });
    }

    @Override
    public int getItemCount() {
        return servers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvDetails;
        ImageButton btnMenu;

        ViewHolder(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_server_icon);
            tvName = v.findViewById(R.id.tv_server_name);
            tvDetails = v.findViewById(R.id.tv_server_details);
            btnMenu = v.findViewById(R.id.btn_server_menu);
        }
    }
}
