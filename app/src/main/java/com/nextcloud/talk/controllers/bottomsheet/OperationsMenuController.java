/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.controllers.bottomsheet;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bluelinelabs.conductor.internal.NoOpControllerChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.CallActivity;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.api.models.json.call.CallOverall;
import com.nextcloud.talk.api.models.json.rooms.Room;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.persistence.entities.UserEntity;
import com.nextcloud.talk.utils.ColorUtils;
import com.nextcloud.talk.utils.ErrorMessageHolder;
import com.nextcloud.talk.utils.bundle.BundleBuilder;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

@AutoInjector(NextcloudTalkApplication.class)
public class OperationsMenuController extends BaseController {
    private static final String TAG = "OperationsMenuController";

    @BindView(R.id.progress_bar)
    ProgressBar progressBar;

    @BindView(R.id.result_image_view)
    ImageView resultImageView;

    @BindView(R.id.result_text_view)
    TextView resultsTextView;

    @BindView(R.id.ok_button)
    Button okButton;

    @Inject
    NcApi ncApi;

    @Inject
    UserUtils userUtils;

    @Inject
    EventBus eventBus;

    private int operationCode;
    private Room room;

    private UserEntity userEntity;
    private String callPassword;

    private Disposable disposable;
    private Disposable secondaryDisposable;

    private int retryCount = 0;

    public OperationsMenuController(Bundle args) {
        super(args);
        this.operationCode = args.getInt(BundleKeys.KEY_OPERATION_CODE);
        this.room = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM));

        this.callPassword = args.getString(BundleKeys.KEY_CALL_PASSWORD, "");
        if (args.containsKey("userEntity")) {
            this.userEntity = Parcels.unwrap(args.getParcelable("userEntity"));
        }
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_operations_menu, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        processOperation();
    }

    private void processOperation() {
        UserEntity userEntity = userUtils.getCurrentUser();
        OperationsObserver operationsObserver = new OperationsObserver();
        if (userEntity != null) {
            String credentials = ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken());
            switch (operationCode) {
                case 1:
                    ncApi.removeSelfFromRoom(credentials, ApiHelper.getUrlForRemoveSelfFromRoom(userEntity.getBaseUrl
                            (), room.getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 2:
                    ncApi.renameRoom(credentials, ApiHelper.getRoom(userEntity.getBaseUrl(), room.getToken()),
                            room.getName())
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 3:
                    ncApi.makeRoomPublic(credentials, ApiHelper.getUrlForRoomVisibility(userEntity.getBaseUrl(), room
                            .getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 4:
                case 5:
                case 6:
                    String pass = "";
                    if (room.getPassword() != null) {
                        pass = room.getPassword();
                    }
                    ncApi.setPassword(credentials, ApiHelper.getUrlForPassword(userEntity.getBaseUrl(),
                            room.getToken()), pass)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 7:
                    // Operation 7 is sharing, so we handle this differently
                    break;
                case 8:
                    ncApi.makeRoomPrivate(credentials, ApiHelper.getUrlForRoomVisibility(userEntity.getBaseUrl(), room
                            .getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 9:
                    ncApi.deleteRoom(credentials, ApiHelper.getUrlForRoomParticipants(userEntity.getBaseUrl(), room.getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                case 99:
                    ncApi.joinRoom(credentials, ApiHelper.getUrlForRoomParticipants(userEntity.getBaseUrl(), room.getToken()),
                            callPassword)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                default:
                    break;
            }
        }
    }

    private void showResultImage(boolean everythingOK) {
        progressBar.setVisibility(View.GONE);

        if (everythingOK) {
            resultImageView.setImageDrawable(ColorUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_check_circle_black_24dp, R.color.nc_darkGreen));
        } else {
            resultImageView.setImageDrawable(ColorUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed));
        }

        resultImageView.setVisibility(View.VISIBLE);

        if (everythingOK) {
            resultsTextView.setText(R.string.nc_all_ok_operation);
        } else {
            resultsTextView.setTextColor(getResources().getColor(R.color.nc_darkRed));
            resultsTextView.setText(R.string.nc_failed_to_perform_operation);
        }

        resultsTextView.setVisibility(View.VISIBLE);
        if (everythingOK) {
            eventBus.post(new BottomSheetLockEvent(true, 2500, true, true));
        } else {
            resultImageView.setImageDrawable(ColorUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed));
            okButton.setOnClickListener(v -> eventBus.post(new BottomSheetLockEvent(true, 0, operationCode != 99,
                    true)));
            okButton.setVisibility(View.VISIBLE);
        }
    }

    private void dispose() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }

        disposable = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dispose();
    }

    private class OperationsObserver implements Observer {

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
            retryCount++;
        }

        @Override
        public void onNext(Object o) {
            if (operationCode != 99) {
                showResultImage(true);
            } else {
                BundleBuilder bundleBuilder = new BundleBuilder(new Bundle());
                bundleBuilder.putString("roomToken", room.getToken());
                bundleBuilder.putParcelable("userEntity", Parcels.wrap(userEntity));
                bundleBuilder.putString(BundleKeys.KEY_CALL_SESSION, ((CallOverall) o).getOcs().getData().getSessionId());
                overridePushHandler(new NoOpControllerChangeHandler());
                overridePopHandler(new NoOpControllerChangeHandler());
                Intent callIntent = new Intent(getActivity(), CallActivity.class);
                callIntent.putExtras(bundleBuilder.build());
                startActivity(callIntent);
            }
        }

        @Override
        public void onError(Throwable e) {
            if (retryCount == 1) {
                if (operationCode != 99 || !(e instanceof HttpException)) {
                    showResultImage(false);
                } else {
                    if (((HttpException) e).response().code() == 403) {
                        eventBus.post(new BottomSheetLockEvent(true, 0, false,
                                false));
                        ErrorMessageHolder.getInstance().setMessageType(ErrorMessageHolder.ErrorMessageType.CALL_PASSWORD_WRONG);
                        getRouter().popCurrentController();
                    } else {
                        showResultImage(false);
                    }
                }
            }
            dispose();
        }

        @Override
        public void onComplete() {
            dispose();
        }
    }
}
