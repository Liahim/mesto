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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static bg.mrm.mesto.Globals.TAG;


public final class Database {
    private Database.Helper mDbHelper;

    private final class Helper extends SQLiteOpenHelper {

        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "peers.db";

        public Helper(final Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
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


    public Future<?> insertInPeers(final PeerRegistry.PeerDescriptor pd) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    insertPeerImpl(pd);
                } catch (final SQLiteException e) {
                    Log.e(TAG, "insertPeer exception", e);
                }
            }
        };

        return mExecutor.submit(r);
    }

    private void insertPeerImpl(final PeerRegistry.PeerDescriptor pd) {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            deletePeerHelper(db, pd.udn);

            final ContentValues values = new ContentValues();
            values.put(PeerSchema.COLUMN_NAME_UDN, pd.udn);
            values.put(PeerSchema.COLUMN_NAME_TITLE, pd.title);

            for (int i = 0; i < pd.endPoints.length; ++i) {
                final PeerRegistry.Endpoint e = pd.endPoints[i];

                values.put(PeerSchema.COLUMN_NAME_URI, e.uri);
                values.put(PeerSchema.COLUMN_NAME_SSID, e.ssid);
                values.put(PeerSchema.COLUMN_NAME_EXTERNAL, e.external);
                values.put(PeerSchema.COLUMN_NAME_START_PORT, e.portRange[0]);
                values.put(PeerSchema.COLUMN_NAME_END_PORT, e.portRange[1]);

                final long rid = db.insert(PeerSchema.TABLE_NAME, null, values);
                Log.i(TAG, "inserted peer endpoint row, id: " + rid);
            }

        } finally {
            db.close();
        }
    }

    public interface Callback {
        void onReadPeer(PeerRegistry.PeerDescriptor pd);
    }

    public void getAllPeers(final Callback cb) {
        final SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            final String sortOrder = PeerSchema.COLUMN_NAME_LAST_MODIFIED + " DESC";
            c = db.query(PeerSchema.TABLE_NAME, null, null, null, null, null, sortOrder);

            if (c.moveToFirst()) {
                final List<PeerRegistry.Endpoint> ee = new ArrayList<PeerRegistry.Endpoint>();
                final PeerRegistry.PeerDescriptor pd = new PeerRegistry.PeerDescriptor();
                pd.title = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_TITLE));
                pd.udn = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_UDN));
                pd.paired = true;

                do {
                    final int[] portRange = new int[]{
                            c.getInt(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_START_PORT)),
                            c.getInt(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_END_PORT))
                    };
                    final PeerRegistry.Endpoint e = new PeerRegistry.Endpoint(
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

    public Future<?> removePeer(final String udn) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    removePeerImpl(udn);
                } catch (final SQLiteException e) {
                    Log.e(TAG, "removePeer exception", e);
                }
            }
        };

        return mExecutor.submit(r);
    }

    private void removePeerImpl(final String udn) {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            deletePeerHelper(db, udn);
        } finally {
            db.close();
        }
    }

    private static void deletePeerHelper(final SQLiteDatabase db, final String udn) {
        final String selection = PeerSchema.COLUMN_NAME_UDN + " LIKE ?";
        final String[] selectionArgs = {udn};
        db.delete(PeerSchema.TABLE_NAME, selection, selectionArgs);
    }

    public static PeerRegistry.PeerDescriptor updatePeer(final Helper helper, final String udn, final long lastModified) {
        try {
            final SQLiteDatabase db = helper.getWritableDatabase();

            final ContentValues values = new ContentValues();
            values.put(PeerSchema.COLUMN_NAME_LAST_MODIFIED, lastModified);

            final String selection = PeerSchema.COLUMN_NAME_UDN + " LIKE ?";
            final String[] selectionArgs = {String.valueOf(lastModified)};

            db.update(PeerSchema.TABLE_NAME, values, selection, selectionArgs);
        } catch (final SQLiteException e) {
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
                + COLUMN_NAME_UDN + " TEXT NOT NULL,"
                + COLUMN_NAME_URI + " TEXT NOT NULL,"
                + COLUMN_NAME_TITLE + " TEXT NOT NULL,"
                + COLUMN_NAME_SSID + " TEXT,"
                + COLUMN_NAME_START_PORT + " INTEGER,"
                + COLUMN_NAME_END_PORT + " INTEGER,"
                + COLUMN_NAME_EXTERNAL + " INTEGER,"
                + COLUMN_NAME_LAST_MODIFIED + " INTEGER)";

        private static final String SQL_DELETE_TABLE_PEERS = "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    private final ExecutorService mExecutor;

    public Database(final Context ctx) {
        mDbHelper = new Helper(ctx);
        mExecutor = Executors.newSingleThreadExecutor();
    }
}
