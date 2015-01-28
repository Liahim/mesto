package bg.mrm.mesto;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;

class NewPeerRegistry extends PeerRegistry {

    void initialize(final Context ctx, final ExecutorService executor, String udn) {
        mOwnId = udn;
        super.initialize(ctx, executor);
    }

    private NewPeerRegistry() {
        super("fakeid");
    }

    static NewPeerRegistry getInstance() {
        return Holder.INSTANCE;
    }

    private final static class Holder {
        static NewPeerRegistry INSTANCE = new NewPeerRegistry();
    }

    @Override
    public List<String> exportOwnEndpoints() {
        return null;
    }
}
