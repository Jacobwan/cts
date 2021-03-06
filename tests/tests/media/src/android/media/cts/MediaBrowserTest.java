/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.media.cts;

import android.content.ComponentName;
import android.cts.util.PollingCheck;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.test.InstrumentationTestCase;

import java.util.List;

/**
 * Test {@link android.media.browse.MediaBrowser}.
 */
public class MediaBrowserTest extends InstrumentationTestCase {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;
    private static final ComponentName TEST_BROWSER_SERVICE = new ComponentName(
            "com.android.cts.media", "android.media.cts.StubMediaBrowserService");
    private static final ComponentName TEST_INVALID_BROWSER_SERVICE = new ComponentName(
            "invalid.package", "invalid.ServiceClassName");
    private final StubConnectionCallback mConnectionCallback = new StubConnectionCallback();
    private final StubSubscriptionCallback mSubscriptionCallback = new StubSubscriptionCallback();
    private final StubItemCallback mItemCallback = new StubItemCallback();

    private MediaBrowser mMediaBrowser;

    public void testMediaBrowser() {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        assertEquals(false, mMediaBrowser.isConnected());

        connectMediaBrowserService();
        assertEquals(true, mMediaBrowser.isConnected());

        assertEquals(TEST_BROWSER_SERVICE, mMediaBrowser.getServiceComponent());
        assertEquals(StubMediaBrowserService.MEDIA_ID_ROOT, mMediaBrowser.getRoot());
        assertEquals(StubMediaBrowserService.EXTRAS_VALUE,
                mMediaBrowser.getExtras().getString(StubMediaBrowserService.EXTRAS_KEY));
        assertEquals(StubMediaBrowserService.sSession.getSessionToken(),
                mMediaBrowser.getSessionToken());

        mMediaBrowser.disconnect();
        assertEquals(false, mMediaBrowser.isConnected());
    }

    public void testConnectTwice() {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        try {
            mMediaBrowser.connect();
            fail();
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testConnectionFailed() {
        resetCallbacks();
        createMediaBrowser(TEST_INVALID_BROWSER_SERVICE);

        mMediaBrowser.connect();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mConnectionCallback.mConnectionFailedCount > 0
                        && mConnectionCallback.mConnectedCount == 0
                        && mConnectionCallback.mConnectionSuspendedCount == 0;
            }
        }.run();
    }

    public void testGetServiceComponentBeforeConnection() {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        try {
            ComponentName serviceComponent = mMediaBrowser.getServiceComponent();
            fail();
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testSubscribe() {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mChildrenLoadedCount > 0;
            }
        }.run();

        assertEquals(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback.mLastParentId);
        assertEquals(StubMediaBrowserService.MEDIA_ID_CHILDREN.length,
                mSubscriptionCallback.mLastChildMediaItems.size());
        for (int i = 0; i < StubMediaBrowserService.MEDIA_ID_CHILDREN.length; i++) {
            assertEquals(StubMediaBrowserService.MEDIA_ID_CHILDREN[i],
                    mSubscriptionCallback.mLastChildMediaItems.get(i).getMediaId());
        }
    }

    public void testGetItem() {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastMediaItem != null;
            }
        }.run();

        assertEquals(StubMediaBrowserService.MEDIA_ID_CHILDREN[0],
                mItemCallback.mLastMediaItem.getMediaId());
    }

    public void testGetItemFailure() {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        mMediaBrowser.getItem("does-not-exist", mItemCallback);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastErrorId != null;
            }
        }.run();

        assertEquals("does-not-exist", mItemCallback.mLastErrorId);
    }

    private void createMediaBrowser(final ComponentName component) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mMediaBrowser = new MediaBrowser(getInstrumentation().getTargetContext(),
                        component, mConnectionCallback, null);
            }
        });
    }

    private void connectMediaBrowserService() {
        mMediaBrowser.connect();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mConnectionCallback.mConnectedCount > 0;
            }
        }.run();
    }

    private void resetCallbacks() {
        mConnectionCallback.reset();
        mSubscriptionCallback.reset();
        mItemCallback.reset();
    }

    private static class StubConnectionCallback extends MediaBrowser.ConnectionCallback {
        volatile int mConnectedCount;
        volatile int mConnectionFailedCount;
        volatile int mConnectionSuspendedCount;

        public void reset() {
            mConnectedCount = 0;
            mConnectionFailedCount = 0;
            mConnectionSuspendedCount = 0;
        }

        @Override
        public void onConnected() {
            mConnectedCount++;
        }

        @Override
        public void onConnectionFailed() {
            mConnectionFailedCount++;
        }

        @Override
        public void onConnectionSuspended() {
            mConnectionSuspendedCount++;
        }
    }

    private static class StubSubscriptionCallback extends MediaBrowser.SubscriptionCallback {
        private volatile int mChildrenLoadedCount;
        private volatile String mLastErrorId;
        private volatile String mLastParentId;
        private volatile List<MediaBrowser.MediaItem> mLastChildMediaItems;

        public void reset() {
            mChildrenLoadedCount = 0;
            mLastErrorId = null;
            mLastParentId = null;
            mLastChildMediaItems = null;
        }

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
            mChildrenLoadedCount++;
            mLastParentId = parentId;
            mLastChildMediaItems = children;
        }

        @Override
        public void onError(String id) {
            mLastErrorId = id;
        }
    }

    private static class StubItemCallback extends MediaBrowser.ItemCallback {
        private volatile MediaBrowser.MediaItem mLastMediaItem;
        private volatile String mLastErrorId;

        public void reset() {
            mLastMediaItem = null;
            mLastErrorId = null;
        }

        @Override
        public void onItemLoaded(MediaItem item) {
            mLastMediaItem = item;
        }

        @Override
        public void onError(String id) {
            mLastErrorId = id;
        }
    }
}
