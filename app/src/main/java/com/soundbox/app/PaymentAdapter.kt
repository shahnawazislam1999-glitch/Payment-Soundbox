package com.soundbox.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentAdapter : RecyclerView.Adapter<PaymentAdapter.VH>() {

    private var items: List<Payment> = emptyList()
    private val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    fun submit(list: List<Payment>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val amount: TextView = v.findViewById(R.id.tvAmount)
        val app: TextView = v.findViewById(R.id.tvApp)
        val time: TextView = v.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_payment, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.amount.text = "₹${p.amount}"
        holder.app.text = "Received from ${p.appName}"
        holder.time.text = fmt.format(Date(p.timeMillis))
    }

    override fun getItemCount(): Int = items.size
}
