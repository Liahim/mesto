package bg.mrm.mesto;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

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
            db.execSQL(PeerEntity.SQL_CREATE_TABLE_PEERS);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.execSQL(PeerEntity.SQL_DELETE_TABLE_PEERS);
            onCreate(db);
        }
    }


    static Future<?> insertInPeersAsync(final Helper helper, final String udn,
                                        final String uri, final String ssid) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                insertInPeers(helper, udn, uri, ssid);
            }
        };

        return helper.mExecutor.submit(r);
    }

    private static void insertInPeers(final Helper helper, final String udn,
                                      final String uri, final String ssid) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(PeerEntity.COLUMN_NAME_UDN, udn);
            values.put(PeerEntity.COLUMN_NAME_URI, uri);
            values.put(PeerEntity.COLUMN_NAME_SSID, ssid);

            long rid = db.insert(PeerEntity.TABLE_NAME, null, values);
        } catch (SQLiteException e) {
            Log.e(TAG, "error inserting into table peers: ", e);
        }
    }

    private static Cursor getAllPeers(Helper helper) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String[] projection = {
                PeerEntity._ID,
                PeerEntity.COLUMN_NAME_UDN,
                PeerEntity.COLUMN_NAME_URI,
                PeerEntity.COLUMN_NAME_SSID
        };

        String sortOrder = PeerEntity.COLUMN_NAME_LAST_MODIFIED + " DESC";
        Cursor c = db.query(PeerEntity.TABLE_NAME, projection, null, null, null, null, sortOrder);
        return c;
    }

    interface Callback {
        void onReadPeer(long rid);
    }

    static void getAllPeers(Helper helper, Callback cb) {
        Cursor c = null;
        try {
            c = getAllPeers(helper);

            c.moveToFirst();
            long rid = c.getLong(c.getColumnIndexOrThrow(PeerEntity._ID));

            cb.onReadPeer(rid);
        } catch (SQLiteException e) {
            if (null != c) {
                c.close();
            }
        }

    }

    static void deletePeer(Helper helper, String udn) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();

            String selection = PeerEntity.COLUMN_NAME_UDN + " LIKE ?";
            String[] selectionArgs = {udn};
            db.delete(PeerEntity.TABLE_NAME, selection, selectionArgs);
        } catch (SQLiteException e) {
            Log.e(TAG, "error deleting peer " + udn, e);
        }
    }

    public static PeerRegistry.PeerDescriptor updatePeer(Helper helper, String udn, long lastModified) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(PeerEntity.COLUMN_NAME_LAST_MODIFIED, lastModified);

            String selection = PeerEntity.COLUMN_NAME_UDN + " LIKE ?";
            String[] selectionArgs = {String.valueOf(lastModified)};

            db.update(PeerEntity.TABLE_NAME, values, selection, selectionArgs);
        } catch (SQLiteException e) {
            Log.e(TAG, "error updating peer " + udn, e);
        }
        return null;
    }

    public static abstract class PeerEntity implements BaseColumns {
        public static final String TABLE_NAME = "peers";
        public static final String COLUMN_NAME_UDN = "udn";
        public static final String COLUMN_NAME_URI = "uri";
        public static final String COLUMN_NAME_START_PORT = "start_port";
        public static final String COLUMN_NAME_END_PORT = "end_port";
        public static final String COLUMN_NAME_SSID = "ssid";
        public static final String COLUMN_NAME_EXTERNAL = "external";
        public static final String COLUMN_NAME_LAST_MODIFIED = "last_modified";


        static final String SQL_CREATE_TABLE_PEERS = "CREATE TABLE "
                + TABLE_NAME + "( " + PeerEntity._ID + " INTEGER PRIMARY KEY,"
                + COLUMN_NAME_UDN + " TEXT NOT NULL UNIQUE,"
                + COLUMN_NAME_URI + " TEXT NOT NULL,"
                + COLUMN_NAME_SSID + " TEXT,"
                + COLUMN_NAME_START_PORT + " INTEGER,"
                + COLUMN_NAME_END_PORT + " INTEGER,"
                + COLUMN_NAME_EXTERNAL + " BOOL,"
                + COLUMN_NAME_LAST_MODIFIED + " TIMESTAMP";

        private static final String SQL_DELETE_TABLE_PEERS = "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    private Database() {
    }
}
