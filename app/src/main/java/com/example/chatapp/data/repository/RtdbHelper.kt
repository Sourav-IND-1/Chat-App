package com.example.chatapp.data.repository

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Single source of truth for the RTDB instance.
 * The explicit URL is required because the database is in the asia-southeast1 region
 * (non-default), otherwise FirebaseDatabase.getInstance() may connect to the wrong endpoint.
 */
object RtdbHelper {
    private const val RTDB_URL =
        "https://chatting-27210-default-rtdb.asia-southeast1.firebasedatabase.app"

    val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(RTDB_URL)
    }

    val ref: DatabaseReference get() = db.reference
}
