package androidx.recyclerview.selection

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView

val <K> SelectionTracker<K>.adapterDataObserverExt: RecyclerView.AdapterDataObserver
    @SuppressLint("RestrictedApi")
    get() = this.adapterDataObserver
