package bg.mrm.mesto.registry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static bg.mrm.mesto.Globals.TAG;


public final class Database {
    private final ExecutorService mExecutor;
    private final OpenHelper mDbHelper;

    private final class OpenHelper extends SQLiteOpenHelper {

        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "registry.db";

        public OpenHelper(final Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(PeersSchema.SQL_CREATE_TABLE);
            db.execSQL(EndpointsSchema.SQL_CREATE_TABLE);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.execSQL(PeersSchema.SQL_DELETE_TABLE);
            db.execSQL(EndpointsSchema.SQL_DELETE_TABLE);
            onCreate(db);
        }
    }

    public Future<?> savePeer(final PeerRegistry.Peer peer, final PeerRegistry.Endpoint[] ee) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    savePeerImpl(peer, ee);
                } catch (final SQLiteException e) {
                    Log.e(TAG, "savePeer exception", e);
                }
            }
        };

        return mExecutor.submit(r);
    }

    private void savePeerImpl(final PeerRegistry.Peer peer, PeerRegistry.Endpoint[] ee) {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            removePeerImpl(db, peer.id);

            final ContentValues values = new ContentValues();
            {
                values.put(PeersSchema.COLUMN_NAME_ID, peer.id);
                values.put(PeersSchema.COLUMN_NAME_TITLE, peer.title);
                final long rid = db.insert(PeersSchema.TABLE_NAME, null, values);
                Log.i(TAG, "inserted peer row, rid: " + rid);
            }

            for (PeerRegistry.Endpoint e : ee) {
                values.clear();

                values.put(EndpointsSchema.COLUMN_NAME_URI, e.uri);
                values.put(EndpointsSchema.COLUMN_NAME_SSID, e.ssid);
                values.put(EndpointsSchema.COLUMN_NAME_EXTERNAL, e.external);
                values.put(EndpointsSchema.COLUMN_NAME_START_PORT, e.portRange[0]);
                values.put(EndpointsSchema.COLUMN_NAME_END_PORT, e.portRange[1]);

                final long rid = db.insert(EndpointsSchema.TABLE_NAME, null, values);
                Log.i(TAG, "inserted endpoint row, id: " + rid);
            }
        } finally {
            db.close();
        }
    }

    public interface Callback {
        void onReadPeer(PeerRegistry.Peer peer, PeerRegistry.Endpoint[] endpoints);
    }

    public Map<PeerRegistry.Peer,Collection<PeerRegistry.Endpoint>> getAll(final Callback cb) {
        final SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Map<PeerRegistry.Peer,Collection<PeerRegistry.Endpoint>> result
                = new HashMap<PeerRegistry.Peer, Collection<PeerRegistry.Endpoint>>();
        Cursor c = null;
        try {
            c = db.query(PeersSchema.TABLE_NAME, null, null, null, null, null, null);
            if (c.moveToFirst()) {
                do {
                    pd.title = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_TITLE));
                    pd.id = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_UDN));

                } while (c.moveToNext());
            }

            final String sortOrder = EndpointsSchema.COLUMN_NAME_LAST_REACHED + " DESC";
            c = db.query(EndpointsSchema.TABLE_NAME, null, null, null, null, null, sortOrder);

            if (c.moveToFirst()) {
                final List<PeerRegistry.Endpoint> ee = new ArrayList<PeerRegistry.Endpoint>();
                final PeerRegistry.Peer pd = new PeerRegistry.Peer();
                pd.title = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_TITLE));
                pd.id = c.getString(c.getColumnIndexOrThrow(PeerSchema.COLUMN_NAME_UDN));
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

    public Future<?> removePeer(final String peerId) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    removePeerImpl(peerId);
                } catch (final SQLiteException e) {
                    Log.e(TAG, "removePeer exception", e);
                }
            }
        };

        return mExecutor.submit(r);
    }

    private void removePeerImpl(final String peerId) {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            removePeerImpl(db, peerId);
        } finally {
            db.close();
        }
    }

    private void removePeerImpl(SQLiteDatabase writableDb, final String peerId) {
        try {
            deletePeerHelper(writableDb, peerId);
        } finally {
            deleteEndpointsHelper(writableDb, peerId);
        }
    }

    private static void deletePeerHelper(final SQLiteDatabase db, final String id) {
        final String selection = PeersSchema.COLUMN_NAME_ID + " LIKE ?";
        final String[] selectionArgs = {id};
        db.delete(PeersSchema.TABLE_NAME, selection, selectionArgs);
    }

    private static void deleteEndpointsHelper(final SQLiteDatabase db, final String id) {
        final String selection = EndpointsSchema.COLUMN_NAME_PEER_ID + " LIKE ?";
        final String[] selectionArgs = {id};
        db.delete(EndpointsSchema.TABLE_NAME, selection, selectionArgs);
    }

    public static PeerRegistry.Peer updatePeer(final OpenHelper helper, final String udn, final long lastModified) {
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

    public interface PeersSchema extends BaseColumns {
        static final String TABLE_NAME = "peers";

        static final String COLUMN_NAME_ID = "id";
        static final String COLUMN_NAME_TITLE = "title";

        static final String SQL_CREATE_TABLE = "CREATE TABLE "
                + TABLE_NAME + "( " + PeersSchema._ID + " INTEGER PRIMARY KEY,"
                + COLUMN_NAME_ID + " TEXT NOT NULL,"
                + COLUMN_NAME_TITLE + " TEXT NOT NULL)";

        static final String SQL_DELETE_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    public interface EndpointsSchema {
        final String TABLE_NAME = "endpoints";

        static final String COLUMN_NAME_PEER_ID = "peer_id";
        static final String COLUMN_NAME_URI = "uri";
        static final String COLUMN_NAME_START_PORT = "start_port";
        static final String COLUMN_NAME_END_PORT = "end_port";
        static final String COLUMN_NAME_SSID = "ssid";
        static final String COLUMN_NAME_EXTERNAL = "external";
        static final String COLUMN_NAME_LAST_REACHED = "last_reached";

        static final String SQL_CREATE_TABLE = "CREATE TABLE "
                + TABLE_NAME + "( " + COLUMN_NAME_PEER_ID + " TEXT PRIMARY KEY,"
                + COLUMN_NAME_URI + " TEXT NOT NULL,"
                + COLUMN_NAME_SSID + " TEXT,"
                + COLUMN_NAME_START_PORT + " INTEGER,"
                + COLUMN_NAME_END_PORT + " INTEGER,"
                + COLUMN_NAME_EXTERNAL + " INTEGER,"
                + COLUMN_NAME_LAST_REACHED + " INTEGER)";

        static final String SQL_DELETE_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    public Database(final Context ctx) {
        mDbHelper = new OpenHelper(ctx);
        mExecutor = Executors.newSingleThreadExecutor();
    }
}
