package info.zzdjk6.rxjavademo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import com.jakewharton.rxbinding.view.RxView;
import com.jakewharton.rxbinding.widget.RxTextView;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public class MainActivity extends Activity {

    @BindView(R.id.edit_text_phone)
    EditText editTextPhone;

    @BindView(R.id.edit_text_captcha)
    EditText editTextCaptcha;

    @BindView(R.id.button_captcha)
    Button buttonCaptcha;

    @BindView(R.id.button_submit)
    Button buttonSubmit;

    private static final int COUNT_DOWN_MAX = 10;

    // http://reactivex.io/documentation/subject.html
    private PublishSubject<Void> rx_stopCaptchaTimer;

    // There is no Variable type in RxJava like RxSwift, but we can use BehaviorSubject
    private BehaviorSubject<Boolean> rx_captchaTimerRunning;

    private Observable<Boolean> rx_phoneValid;
    private Observable<Boolean> rx_captchaValid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // init hot signals
        initRxPhoneValid();
        initRxCaptchaValid();
        initRxStopCaptchaTimer();
        initRxCaptchaTimerRunning();

        // observe hot signals to change state of buttons
        bindButtonSubmitEnableState();
        bindButtonCaptchaEnableState();

        // react to UI event in Rx way
        // RxCocoa for iOS has similar functionality
        observeButtonCaptchaClick();
        observeButtonSubmitClick();
    }

    private void initRxPhoneValid() {
        rx_phoneValid = RxTextView
            .textChanges(editTextPhone) // signal for text change
            .doOnNext(new Action1<CharSequence>() {
                @Override
                public void call(CharSequence charSequence) {
                    rx_stopCaptchaTimer.onNext(null);
                }
            })
            .map(new Func1<CharSequence, Boolean>() {
                @Override
                public Boolean call(CharSequence charSequence) {
                    return charSequence.length() == 11;
                }
            }); // signal for indicate whether phone number is valid
    }

    private void initRxCaptchaValid() {
        rx_captchaValid = RxTextView
            .textChanges(editTextCaptcha)
            .map(new Func1<CharSequence, Boolean>() {
                @Override
                public Boolean call(CharSequence charSequence) {
                    return charSequence.length() == 4;
                }
            });
    }

    private void initRxStopCaptchaTimer() {
        rx_stopCaptchaTimer = PublishSubject.create();
    }

    private void initRxCaptchaTimerRunning() {
        rx_captchaTimerRunning = BehaviorSubject.create(false);
    }

    private void bindButtonSubmitEnableState() {
        Observable
            .combineLatest(
                rx_phoneValid,
                rx_captchaValid,
                new Func2<Boolean, Boolean, Boolean>() {
                    @Override
                    public Boolean call(Boolean phoneValid, Boolean captchaValid) {
                        return phoneValid && captchaValid;
                    }
                })
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(new Action1<Boolean>() {
                @Override
                public void call(Boolean shouldEnable) {
                    buttonSubmit.setEnabled(shouldEnable);
                }
            })
            .subscribe();
    }

    private void bindButtonCaptchaEnableState() {
        buttonCaptcha.setEnabled(false);
        Observable
            .combineLatest(
                rx_phoneValid,
                rx_captchaTimerRunning,
                new Func2<Boolean, Boolean, Boolean>() {
                    @Override
                    public Boolean call(Boolean phoneValid, Boolean captchaTimerRunning) {
                        return phoneValid && !captchaTimerRunning;
                    }
                })
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(new Action1<Boolean>() {
                @Override
                public void call(Boolean shouldEnable) {
                    buttonCaptcha.setEnabled(shouldEnable);
                }
            })
            .subscribe();
    }

    private void observeButtonCaptchaClick() {
        RxView
            .clicks(buttonCaptcha)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(new Action1<Void>() {
                @Override
                public void call(Void aVoid) {
                    stopCaptchaTimer();
                    startCaptchaTimer();
                }
            })
            .subscribe();
    }

    private void observeButtonSubmitClick() {
        final Context ctx = this;
        RxView
            .clicks(buttonSubmit)
            .observeOn(AndroidSchedulers.mainThread())
            .map(new Func1<Void, String>() {
                @Override
                public String call(Void aVoid) {
                    Random rand = new Random();
                    int randInt = rand.nextInt(10);
                    if (randInt >= 5) {
                        return "Your input data pass the test";
                    }

                    throw new RuntimeException("Bad Luck");
                }
            })
            .doOnError(new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                    builder
                        .setTitle("Oops")
                        .setMessage(throwable.getMessage())
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .create()
                        .show();
                }
            })
            .doOnNext(new Action1<String>() {
                @Override
                public void call(String message) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                    builder
                        .setTitle("Congratulations")
                        .setMessage(message)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .create()
                        .show();
                }
            })
            .retry() // observable will be shutdown when encounter error in RxJava, http://blog.danlew.net/2015/12/08/error-handling-in-rxjava/
            .subscribe();
    }

    private void stopCaptchaTimer() {
        rx_captchaTimerRunning.onNext(false);
    }

    private void startCaptchaTimer() {
        buttonCaptcha.setText(String.format(Locale.ENGLISH, "%d S", COUNT_DOWN_MAX));
        rx_captchaTimerRunning.onNext(true);

        // Deadly simple countdown timer in Rx
        Observable
            .interval(1, TimeUnit.SECONDS)
            .take(COUNT_DOWN_MAX)
            .takeUntil(rx_stopCaptchaTimer)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(new Action1<Long>() {
                @Override
                public void call(Long current) {
                    int remain = (int) (COUNT_DOWN_MAX - current - 1);
                    buttonCaptcha.setText(String.format(Locale.ENGLISH, "%d S", remain));
                }
            })
            .doOnCompleted(new Action0() {
                @Override
                public void call() {
                    rx_captchaTimerRunning.onNext(false);
                    buttonCaptcha.setText(R.string.button_captcha_title);
                }
            })
            .subscribe();
    }
}
