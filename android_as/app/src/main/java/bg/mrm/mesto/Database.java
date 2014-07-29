package bg.mrm.mesto;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static bg.mrm.mesto.Globals.TAG;


public final class Database {
    final static class Helper extends SQLiteOpenHelper {

        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "peers.db";

        private final ExecutorService mExecutor;

        public Helper(final Context context, ExecutorService executor) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mExecutor = executor;
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(PeerSchema.SQL_CREATE_TABLE_PEERS);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.execSQL(PeerSchema.SQL_DELETE_TABLE_PEERS);
            onCreate(db);
        }
    }


    static Future<?> insertInPeersAsync(final Helper helper, final String udn,
                                        final PeerRegistry.PeerDescriptor pd) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                insertPeer(helper, pd);
            }
        };

        return helper.mExecutor.submit(r);
    }

    private static void insertPeer(final Helper helper, final PeerRegistry.PeerDescriptor pd) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            deletePeerHelper(db, pd.udn);

            ContentValues values = new ContentValues();
            values.put(PeerSchema.COLUMN_NAME_UDN, pd.udn);
            values.put(PeerSchema.COLUMN_NAME_TITLE, pd.title);

            for (int i = 0; i < pd.endPoints.length; ++i) {
                PeerRegistry.Endpoint e = pd.endPoints[i];

                values.put(PeerSchema.COLUMN_NAME_URI, e.uri);
                values.put(PeerSchema.COLUMN_NAME_SSID, e.ssid);
                values.put(PeerSchema.COLUMN_NAME_EXTERNAL, e.external);
                values.put(PeerSchema.COLUMN_NAME_START_PORT, e.portRange[0]);
                values.put(PeerSchema.COLUMN_NAME_END_PORT, e.portRange[1]);

                long rid = db.insert(PeerSchema.TABLE_NAME, null, values);
                Log.i(TAG, "inserted peer endpoint row, id: " + rid);
            }

        } finally {
            db.close();
        }
    }

    interface Callback {
        void onReadPeer(PeerRegistry.PeerDescriptor pd);
    }

    static void getAllPeers(Helper helper, Callback cb) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = null;
        try {
            String sortOrder = PeerSchema.COLUMN_NAME_LAST_MODIFIED + " DESC";
            c = db.query(PeerSchema.TABLE_NAME, null, null, null, null, null, sortOrder);

            if (c.moveToFirst()) {
                List<PeerRegistry.Endpoint> ee = new ArrayList<PeerRegistry.Endpoint>();
                PeerRegistry.PeerDescriptor pd = new PeerRegistry.PeerDescriptor();
                pd.title = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_TITLE));
                pd.udn = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_UDN));
                pd.paired = true;

                do {
                    int portRange[] = new int[]{
                            c.getInt(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_START_PORT)),
                            c.getInt(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_END_PORT))
                    };
                    PeerRegistry.Endpoint e = new PeerRegistry.Endpoint(
                            c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_SSID)),
                            c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_URI)),
                            portRange,
                            0 < c.getInt(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_EXTERNAL))
                    );
                    ee.add(e);
                } while (c.moveToNext());

                pd.endPoints = new PeerRegistry.Endpoint[ee.size()];
                pd.endPoints = ee.toArray(pd.endPoints);
                cb.onReadPeer(pd);
            }
        } finally {
            try {
                if (null != c) {
                    c.close();
                }
            } finally {
                db.close();
            }
        }

    }

    private static void deletePeerHelper(final SQLiteDatabase db, final String udn) {
        String selection = PeerSchema.COLUMN_NAME_UDN + " LIKE ?";
        String[] selectionArgs = {udn};
        db.delete(PeerSchema.TABLE_NAME, selection, selectionArgs);
    }

    public static PeerRegistry.PeerDescriptor updatePeer(Helper helper, String udn, long lastModified) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(PeerSchema.COLUMN_NAME_LAST_MODIFIED, lastModified);

            String selection = PeerSchema.COLUMN_NAME_UDN + " LIKE ?";
            String[] selectionArgs = {String.valueOf(lastModified)};

            db.update(PeerSchema.TABLE_NAME, values, selection, selectionArgs);
        } catch (SQLiteException e) {
            Log.e(TAG, "error updating peer " + udn, e);
        }
        return null;
    }

    public static abstract class PeerSchema implements BaseColumns {
        public static final String TABLE_NAME = "peers";
        public static final String COLUMN_NAME_UDN = "udn";
        public static final String COLUMN_NAME_URI = "uri";
        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_START_PORT = "start_port";
        public static final String COLUMN_NAME_END_PORT = "end_port";
        public static final String COLUMN_NAME_SSID = "ssid";
        public static final String COLUMN_NAME_EXTERNAL = "external";
        public static final String COLUMN_NAME_LAST_MODIFIED = "last_modified";


        static final String SQL_CREATE_TABLE_PEERS = "CREATE TABLE "
                + TABLE_NAME + "( " + PeerSchema._ID + " INTEGER PRIMARY KEY,"
                + COLUMN_NAME_UDN + " TEXT NOT NULL UNIQUE,"
                + COLUMN_NAME_URI + " TEXT NOT NULL,"
                + COLUMN_NAME_TITLE + " TEXT NOT NULL,"
                + COLUMN_NAME_SSID + " TEXT,"
                + COLUMN_NAME_START_PORT + " INTEGER,"
                + COLUMN_NAME_END_PORT + " INTEGER,"
                + COLUMN_NAME_EXTERNAL + " INTEGER,"
                + COLUMN_NAME_LAST_MODIFIED + " INTEGER)";

        private static final String SQL_DELETE_TABLE_PEERS = "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    private Database() {
    }
}
