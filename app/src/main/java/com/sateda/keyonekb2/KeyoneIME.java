package com.sateda.keyonekb2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.Keep;
import android.support.annotation.RequiresApi;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sateda.keyonekb2.input.CallStateCallback;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;


@Keep
public class KeyoneIME extends InputMethodServiceCodeCustomizable implements KeyboardView.OnKeyboardActionListener, SpellCheckerSession.SpellCheckerSessionListener, View.OnTouchListener {



    private static final boolean DEBUG = false;

    public static KeyoneIME Instance;



    public String TITLE_NAV_TEXT;
    public String TITLE_NAV_FV_TEXT;
    public String TITLE_SYM_TEXT;
    public String TITLE_SYM2_TEXT;
    public String TITLE_GESTURE_INPUT;
    public String TITLE_GESTURE_INPUT_UP_DOWN;
    public String TITLE_GESTURE_VIEW;
    public String TITLE_GESTURE_OFF;




    private final NotificationProcessor notificationProcessor = new NotificationProcessor();
    private Keyboard onScreenSwipePanelAndLanguage;





    private String _lastPackageName = "";

    public FixedSizeSet<String> PackageHistory = new FixedSizeSet<>(4);

    //settings
    private int pref_height_bottom_bar = 10;


    private boolean pref_flag = false;

    private boolean pref_system_icon_no_notification_text = false;

    //Предзагружаем клавиатуры, чтобы не плодить объекты
    private Keyboard keyboardNavigation;






    KeyboardLayout.KeyboardLayoutOptions.IconRes AltOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltHoldIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymHoldIconRes;

    KeyboardLayout.KeyboardLayoutOptions.IconRes navIconRes;

    KeyboardLayout.KeyboardLayoutOptions.IconRes navFnIconRes;

    public class FixedSizeSet<E> extends AbstractSet<E> {
        private final LinkedHashMap<E, E> contents;

        FixedSizeSet(final int maxCapacity) {
            contents = new LinkedHashMap<E, E>(maxCapacity * 4 /3, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<E, E> eldest) {
                    return size() == maxCapacity;
                }
            };
        }

        @Override
        public Iterator<E> iterator() {
            return contents.keySet().iterator();
        }

        @Override
        public int size() {
            return contents.size();
        }

        public boolean add(E e) {
            boolean hadNull = false;
            if (e == null) {
                hadNull = contents.containsKey(null);
            }
            E previous = contents.put(e, e);
            return e == null ? hadNull : previous != null;
        }

        @Override
        public boolean contains(Object o) {
            return contents.containsKey(o);
        }
    }

    @Override
    public void onDestroy() {
        Instance = null;
        notificationProcessor.CancelAll();
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    @Override
    public synchronized void onCreate() {
        try {
            super.onCreate();
            Instance = this;

            TITLE_NAV_TEXT = getString(R.string.notification_kb_state_nav_mode);
            TITLE_NAV_FV_TEXT = getString(R.string.notification_kb_state_nav_fn_mode);
            TITLE_SYM_TEXT = getString(R.string.notification_kb_state_alt_mode);
            TITLE_SYM2_TEXT = getString(R.string.notification_kb_state_sym_mode);
            TITLE_GESTURE_INPUT = getString(R.string.notification_kb_state_gesture_input);
            TITLE_GESTURE_INPUT_UP_DOWN = getString(R.string.notification_kb_state_gesture_input_up_down);
            TITLE_GESTURE_VIEW = getString(R.string.notification_kb_state_gesture_view);
            TITLE_GESTURE_OFF = getString(R.string.notification_kb_state_gesture_off);

            AltOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt_one, R.drawable.ic_kb_alt_one);
            AltAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt_all);
            AltHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt);
            SymOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym_one, R.drawable.ic_kb_sym_one);
            SymAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym_all);
            SymHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym);

            navIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav, R.drawable.ic_kb_nav);
            navFnIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav_fn, R.drawable.ic_kb_nav_fn);

            callStateCallback = new CallStateCallback();
            telephonyManager = getTelephonyManager();
            telecomManager = getTelecomManager();

            if (telephonyManager != null) {
                telephonyManager.listen(callStateCallback, PhoneStateListener.LISTEN_CALL_STATE);
            }


            pref_height_bottom_bar = 10;

            pref_show_toast = false;
            pref_alt_space = true;
            pref_long_press_key_alt_symbol = false;

            keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
            LoadSettingsAndKeyboards();
            LoadKeyProcessingMechanics(this);

            onScreenSwipePanelAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

            keyboardView = (ViewSatedaKeyboard) getLayoutInflater().inflate(R.layout.keyboard, null);
            keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
            keyboardView.setOnKeyboardActionListener(this);
            keyboardView.setPreviewEnabled(false);
            keyboardView.setService(this);
            keyboardView.clearAnimation();
            keyboardView.showFlag(pref_flag);

            keyboardNavigation = new Keyboard(this, R.xml.navigation);

            notificationProcessor.Initialize(getApplicationContext());
            UpdateGestureModeVisualization(false);
            UpdateKeyboardModeVisualization();
        } catch(Throwable ex) {
            Log.e(TAG2, "onCreate Exception: "+ex);
            throw ex;
        }
    }


    @Override
    public void onPress(int primaryCode) {
        Log.d(TAG2, "onPress");
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onFinishInput() {

        //TODO: Не смог понять нафига это нужно
        if (symbolOnScreenKeyboardMode) {
            symbolOnScreenKeyboardMode = false;
            altPressSingleSymbolAltedMode = false;
            doubleAltPressAllSymbolsAlted = false;
            mode_keyboard_gestures = false;
            mode_keyboard_gestures_plus_up_down = false;
            UpdateKeyboardModeVisualization();
            UpdateGestureModeVisualization(false);
        }

        if (_lastPackageName.equals("com.sateda.keyonekb2")) LoadSettingsAndKeyboards();

        //TODO: Подумать, чтобы не надо было инициализировать свайп-клавиаутуру по настройке pref_show_default_onscreen_keyboard
        keyboardView.showFlag(pref_flag);
        if (onScreenSwipePanelAndLanguage.getHeight() != 70 + pref_height_bottom_bar * 5)
            onScreenSwipePanelAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

        Log.d(TAG2, "onFinishInput ");

    }

    @Override
    public synchronized void onStartInput(EditorInfo editorInfo, boolean restarting) {
        super.onStartInput(editorInfo, restarting);
        //TODO: Минорно. Если надо знать какие флаги их надо расшифровывать
        Log.d(TAG2, "onStartInput package: " + editorInfo.packageName
                + " editorInfo.inputType: "+Integer.toBinaryString(editorInfo.inputType)
                +" editorInfo.imeOptions: "+Integer.toBinaryString(editorInfo.imeOptions));

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.

        if(     SearchPluginLauncher != null
                && !editorInfo.packageName.equals(SearchPluginLauncher.PackageName)) {
            SetSearchHack(null);
        }

        // Обрабатываем переход между приложениями
        if (!editorInfo.packageName.equals(_lastPackageName)) {
            PackageHistory.add(_lastPackageName);
            _lastPackageName = editorInfo.packageName;

            //Отключаем режим навигации
            keyboardStateFixed_NavModeAndKeyboard = false;
            fnSymbolOnScreenKeyboardMode = false;
            keyboardView.SetFnKeyboardMode(false);

            UpdateGestureModeVisualization();
        }

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (editorInfo.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                doubleAltPressAllSymbolsAlted = true;
                break;

            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                doubleAltPressAllSymbolsAlted = false;
                altPressSingleSymbolAltedMode = false;

                // We now look for a few special variations of text that will
                // modify our behavior.
                //int variation = editorInfo.inputType & InputType.TYPE_MASK_VARIATION;
                //if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                //        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    //mPredictionOn = false;
                //}

                //if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                //        || variation == InputType.TYPE_TEXT_VARIATION_URI
                //        || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    //mPredictionOn = false;
                //}

                //if ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    //mPredictionOn = false;
                    //mCompletionOn = isFullscreenMode();
                //}

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                DetermineFirstBigCharAndReturnChangedState(editorInfo);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                ResetMetaState();
                DetermineFirstBigCharAndReturnChangedState(editorInfo);
        }

        //Это нужно чтобы показать клаву (перейти в режим редактирования)
        if (pref_show_default_onscreen_keyboard
                && !isInputViewShown()
                && getCurrentInputConnection() != null
                && IsInputMode()
                && Orientation == 1) {
            this.showWindow(true);
        }

        UpdateGestureModeVisualization(IsInputMode());
        UpdateKeyboardModeVisualization();
        // Update the label on the enter key, depending on what the application
        // says it will do.
    }

    public void Vibrate(int ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if(v == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            v.vibrate(ms);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public synchronized boolean onKeyDown(int keyCode, KeyEvent event) {
        keyboardView.hidePopup(false);
        Log.v(TAG2, "onKeyDown " + event);

        //TODO: Hack 4 pocket
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }

        if(
            !IsInputMode()
            && IsViewModeKeyCode(keyCode, event.getMetaState())
            && SearchPluginLauncher == null
            && !IsNavMode())  {
            Log.d(TAG2, "App transparency mode");
            return super.onKeyDown(keyCode, event);
        }

        //Vibrate(50);

        //region Режим "Навигационные клавиши"

        int navigationKeyCode;
        InputConnection inputConnection = getCurrentInputConnection();
        if (IsNavMode() && IsNavKeyCode(keyCode)) {
            int scanCode = event.getScanCode();
            navigationKeyCode = getNavigationCode(scanCode);

            Log.d(TAG2, "navigationKeyCode " + navigationKeyCode);
            if (navigationKeyCode == -7) {
                fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                UpdateKeyboardModeVisualization();
                keyboardView.SetFnKeyboardMode(fnSymbolOnScreenKeyboardMode);
                return true;
            }
            if (inputConnection != null && navigationKeyCode != 0) {
                //Удаляем из meta состояния SYM т.к. он мешает некоторым приложениям воспринимать NAV символы с зажатием SYM
                int meta = event.getMetaState() & ~KeyEvent.META_SYM_ON;
                keyDownUpMeta(navigationKeyCode, inputConnection, meta);
                //keyDownUpDefaultFlags(navigationKeyCode, inputConnection);
            }
            return true;
        }
        //endregion

        needUpdateVisualInsideSingleEvent = false;
        boolean processed = ProcessNewStatusModelOnKeyDown(keyCode, event);
        if (!processed)
            return false;


        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        return keyCode != KeyEvent.KEYCODE_SHIFT_LEFT;
    }

    @Override
    public synchronized boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.v(TAG2, "onKeyUp " + event);

        //TODO: Hack 4 pocket
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }

        //region Блок навигационной клавиатуры

        if (IsNavMode() && IsNavKeyCode(keyCode)) {
            return true;
        }
        //endregion

        if(
            !IsInputMode()
            && FindAtKeyDownList(keyCode, event.getScanCode()) == null
            && IsViewModeKeyCode(keyCode, event.getMetaState())
            && SearchPluginLauncher == null)  {

            Log.d(TAG2, "App transparency mode");
            return super.onKeyUp(keyCode, event);
        }

        needUpdateVisualInsideSingleEvent = false;
        boolean processed = ProcessNewStatusModelOnKeyUp(keyCode, event);
        if (!processed)
            return false;

        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        return keyCode != KeyEvent.KEYCODE_SHIFT_LEFT;
    }

    //TODO: Вынести в XML
    public int getNavigationCode(int scanCode) {
        int keyEventCode = 0;
        switch (scanCode) {
            case 16: //Q
                keyEventCode = 111; //ESC
                break;
            case 17: //W (1)
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 131; //F1
                    break;
                }
            case 21: //Y
                keyEventCode = 122; //Home
                break;
            case 18: //E (2)
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 132; //F2
                    break;
                }
            case 22: //U
                keyEventCode = 19; //Arrow Up
                break;
            case 19: //R (3)
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 133; //F3
                    break;
                }
            case 23: //I
                keyEventCode = 123; //END
                break;
            case 20: //T
            case 24: //O
                keyEventCode = 92; //Page Up
                break;
            case 25: //P
                keyEventCode = -7; //FN
                break;

            case 30: //A
                keyEventCode = 61; //Tab
                break;
            case 31: //S
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 134; //F4
                    break;
                }
            case 35: //H
                keyEventCode = 21; //Arrow Left
                break;
            case 32: //D
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 135; //F5
                    break;
                }
            case 36: //J
                keyEventCode = 20; //Arrow Down
                break;
            case 33: //F
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 135; //F5
                    break;
                }
            case 37: //K
                keyEventCode = 22; //Arrow Right
                break;
            case 34: //G
            case 38: //L
                keyEventCode = 93; //Page down
                break;

            case 44: //Z (7)
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 137; //F7
                break;
            case 45: //X (8)
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 138; //F8
                break;
            case 46: //C (9)
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 139; //F9
                break;

            case 11: //0
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 140; //F10
                break;

            default:
        }

        return keyEventCode;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        Log.d(TAG2, "onKeyLongPress " + event);
        return false;
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        Log.d(TAG2, "onCreateInputView");
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    //private int lastOrientation = 0;
    private int lastVisibility = -1;

    private int Orientation = 1;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (newConfig.orientation == 2) {
            Orientation = 2;
            if (keyboardView.getVisibility() == View.VISIBLE) {
                lastVisibility = View.VISIBLE;
                keyboardView.setVisibility(View.GONE);
            }
        } else if (newConfig.orientation == 1) {
            Orientation = 1;
            if (lastVisibility == View.VISIBLE) {
                keyboardView.setVisibility(View.VISIBLE);
            } else
                keyboardView.setVisibility(View.GONE);
        }

    }

    @Override
    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
                                  final int newSelStart, final int newSelEnd,
                                  final int composingSpanStart, final int composingSpanEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd);
        if (DEBUG) {
            Log.i(TAG2, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd);
        }

        // This call happens whether our view is displayed or not, but if it's not then we should
        // not attempt recorrection. This is true even with a hardware keyboard connected: if the
        // view is not displayed we have no means of showing suggestions anyway, and if it is then
        // we want to show suggestions anyway.

        ProcessOnCursorMovement(getCurrentInputEditorInfo());

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        Log.d(TAG2, "onKey " + primaryCode);
        InputConnection inputConnection = getCurrentInputConnection();
        playClick(primaryCode);
        if (keyboardStateFixed_NavModeAndKeyboard) {
            switch (primaryCode) {

                case 19: //UP
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 20: //DOWN
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case 21: //LEFT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case 0: //SPACE
                case 32: //SPACE
                    break;

                case 111: //ESC
                case 61:  //TAB
                case 122: //HOME
                case 123: //END
                case 92:  //Page UP
                case 93:  //Page DOWN
                    keyDownUpNoMetaKeepTouch(primaryCode, inputConnection);
                    break;

                case -7:  //Switch F1-F12
                    fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                    UpdateKeyboardModeVisualization();
                    keyboardView.SetFnKeyboardMode(fnSymbolOnScreenKeyboardMode);
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_ENTER, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                default:

            }
        } else {
            switch (primaryCode) {
                //Хак чтобы не ставился пробел после свайпа по свайп-анели
                case 0: //SPACE
                case 32: //SPACE
                    break;
                case 21: //LEFT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_ENTER, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                default:
                    char code = (char) primaryCode;
                    inputConnection.commitText(String.valueOf(code), 1);
            }
        }

    }

    @Override
    public void onText(CharSequence text) {
        Log.d(TAG2, "onText: " + text);
    }

    @Override
    public void swipeLeft() {
        LogKeyboardTest("swipeLeft");
    }

    @Override
    public void swipeRight() {
        LogKeyboardTest("swipeRight");
    }

    @Override
    public void swipeDown() {
        LogKeyboardTest("swipeDown");

    }

    @Override
    public void swipeUp() {
        LogKeyboardTest("swipeUp");
    }

    float lastGestureX = 0;

    void printSamples(MotionEvent ev) {
        final int historySize = ev.getHistorySize();
        final int pointerCount = ev.getPointerCount();
        for (int h = 0; h < historySize; h++) {
            LogKeyboardTest(String.format("At time %d:", ev.getHistoricalEventTime(h)));
            for (int p = 0; p < pointerCount; p++) {
                LogKeyboardTest(String.format("  pointer HISTORY %d: (%f,%f)",
                        ev.getPointerId(p), ev.getHistoricalX(p, h), ev.getHistoricalY(p, h)));
            }
        }
        LogKeyboardTest(String.format("At time %d:", ev.getEventTime()));
        for (int p = 0; p < pointerCount; p++) {
            LogKeyboardTest(String.format("  pointer %d: RAW (%f,%f)",
                    ev.getPointerId(p), ev.getRawX(), ev.getRawY()));
            MotionEvent.PointerCoords pc =  new MotionEvent.PointerCoords();
            ev.getPointerCoords(p, pc);
            LogKeyboardTest(String.format("  pointer %d: PC (%f,%f)",
                    ev.getPointerId(p), pc.x, pc.y));

            LogKeyboardTest(String.format("  pointer %d: AXIS (%f,%f)",
                    ev.getPointerId(p), ev.getAxisValue(MotionEvent.AXIS_VSCROLL), ev.getAxisValue(MotionEvent.AXIS_HSCROLL)));
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        LogKeyboardTest("onTouch: "+motionEvent.getAction());
        printSamples(motionEvent);


        //Log.d(TAG, "onTouch "+motionEvent);
        InputConnection inputConnection = getCurrentInputConnection();
        int motionEventAction = motionEvent.getAction();
        if (!symbolOnScreenKeyboardMode) {
            if (motionEventAction == MotionEvent.ACTION_DOWN) lastGestureX = motionEvent.getX();
            if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX + (36 - pref_gesture_motion_sensitivity) < motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorRightSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    LogKeyboardTest(TAG2, "onTouch KEYCODE_DPAD_RIGHT " + motionEvent);
                }
            } else if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX - (36 - pref_gesture_motion_sensitivity) > motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorLeftSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    LogKeyboardTest(TAG2, "onTouch KEYCODE_DPAD_LEFT " + motionEvent);
                }
            }
        } else {
            //alt-popup-keyboard
            if (motionEventAction == MotionEvent.ACTION_MOVE)
                keyboardView.coordsToIndexKey(motionEvent.getX());
            if (motionEventAction == MotionEvent.ACTION_UP) keyboardView.hidePopup(true);
        }
        return false;
    }

     @Override
    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
        //Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);

        return ProcessGestureAtMotionEvent(motionEvent);
    }


    public boolean IsSym2Mode() {
        return MetaIsAltMode() && IsShiftModeOrSymPadAltShiftMode();
    }

    private boolean IsShiftModeOrSymPadAltShiftMode() {
        return MetaIsShiftPressed() || MetaIsSymPadAltShiftMode();
    }






    //region VISUAL UPDATE


    protected void UpdateGestureModeVisualization() {
        UpdateGestureModeVisualization(IsInputMode());
    }

    @Override
    protected void UpdateGestureModeVisualization(boolean isInput) {
        boolean changed;

        if (isInput && mode_keyboard_gestures && !IsNoGesturesMode()) {

            if (mode_keyboard_gestures_plus_up_down) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input_up_down);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT_UP_DOWN);
            } else {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT);
            }
        } else if (!isInput && pref_keyboard_gestures_at_views_enable && !IsNoGesturesMode()) {
            changed = setSmallIcon2(R.mipmap.ic_gesture_icon_view);
            changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_VIEW);
        } else {
            changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
            changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
        }
        if (changed)
            notificationProcessor.UpdateNotificationGestureMode();
    }

    private boolean setSmallIcon2(int resId) {
        return notificationProcessor.SetSmallIconGestureMode(resId);
    }


    private void HideSwipePanelOnHidePreferenceAndVisibleState() {
        if (!pref_show_default_onscreen_keyboard) {
            HideKeyboard();
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void HideKeyboard() {
        keyboardView.setOnTouchListener(null);
        if (keyboardView.getVisibility() == View.VISIBLE) {
            keyboardView.setVisibility(View.GONE);
        }
        this.hideWindow();

    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void ShowKeyboard() {
        keyboardView.setOnTouchListener(this);
        if (keyboardView.getVisibility() != View.VISIBLE)
            keyboardView.setVisibility(View.VISIBLE);
        if (!keyboardView.isShown())
            this.showWindow(true);
    }

    @Override
    protected void UpdateKeyboardModeVisualization() {
        UpdateKeyboardModeVisualization(pref_show_default_onscreen_keyboard);
    }

    @Override
    protected void UpdateKeyboardModeVisualization(boolean updateSwipePanelData) {
        Log.d(TAG2, "UpdateKeyboardModeVisualization oneTimeShiftOneTimeBigMode=" + oneTimeShiftOneTimeBigMode + " doubleShiftCapsMode=" + doubleShiftCapsMode + " doubleAltPressAllSymbolsAlted=" + doubleAltPressAllSymbolsAlted + " altPressSingleSymbolAltedMode=" + altPressSingleSymbolAltedMode);
        KeyboardLayout keyboardLayout = keyboardLayoutManager.GetCurrentKeyboardLayout();

        String languageOnScreenNaming = keyboardLayout.KeyboardName;
        boolean changed;
        boolean needUsefulKeyboard = false;
        if (IsNavMode()) {
            if (!fnSymbolOnScreenKeyboardMode) {
                changed = UpdateNotification(navIconRes, TITLE_NAV_TEXT);
            } else {
                changed = UpdateNotification(navFnIconRes, TITLE_NAV_FV_TEXT);
            }
            //onScreenKeyboardSymbols = keyboardNavigation;
            keyboardView.setKeyboard(keyboardNavigation);
            keyboardView.setNavigationLayer();
            needUsefulKeyboard = true;
        } else if (symbolOnScreenKeyboardMode) {

            if (IsSym2Mode()) {
                changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM_TEXT);
            }

            keyboardView.setKeyboard(keyboardLayoutManager.GetSymKeyboard(symPadAltShift));
            keyboardView.setAltLayer(keyboardLayoutManager.GetCurrentKeyboardLayout(), symPadAltShift);

            needUsefulKeyboard = true;

        } else if (doubleAltPressAllSymbolsAlted || metaAltPressed) {
            if (IsSym2Mode()) {
                if(metaAltPressed)
                    changed = UpdateNotification(SymHoldIconRes, TITLE_SYM2_TEXT);
                else
                    changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else if (metaAltPressed){
                changed = UpdateNotification(AltHoldIconRes, TITLE_SYM_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM2_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData);
        } else if (altPressSingleSymbolAltedMode) {
            if (IsSym2Mode()) {
                changed = UpdateNotification(SymOneIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltOneIconRes, TITLE_SYM_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData);
        } else if (doubleShiftCapsMode || metaShiftPressed) {
            changed = UpdateNotification(keyboardLayout.Resources.IconCapsRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftMode(updateSwipePanelData, languageOnScreenNaming);
        } else if (oneTimeShiftOneTimeBigMode) {
            changed = UpdateNotification(keyboardLayout.Resources.IconFirstShiftRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftOneMode(updateSwipePanelData, languageOnScreenNaming);
        } else {
            // Случай со строными буквами
            changed = UpdateNotification(keyboardLayout.Resources.IconLittleRes, languageOnScreenNaming);
            UpdateKeyboardViewLetterMode(updateSwipePanelData, languageOnScreenNaming);
        }

        if (needUsefulKeyboard)
            if (IsInputMode())
                ShowKeyboard();
            else
                HideKeyboard();
        else
            HideSwipePanelOnHidePreferenceAndVisibleState();

        if (changed && !pref_system_icon_no_notification_text)
            notificationProcessor.UpdateNotificationLayoutMode();
    }

    private void UpdateKeyboardViewShiftOneMode(boolean updateSwipePanelData, String languageOnScreenNaming) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setShiftFirst();
            keyboardView.setLetterKB();
        }
    }

    private void UpdateKeyboardViewLetterMode(boolean updateSwipePanelData, String languageOnScreenNaming) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            keyboardView.notShift();
            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setLetterKB();
        }
    }

    private void UpdateKeyboardViewShiftMode(boolean updateSwipePanelData, String languageOnScreenNaming) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setShiftAll();
            keyboardView.setLetterKB();
        }
    }

    private void UpdateKeyboardViewAltMode(boolean updateSwipePanelData) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            if (IsSym2Mode()) {
                keyboardView.setLang(TITLE_SYM2_TEXT);
            } else {
                keyboardView.setLang(TITLE_SYM_TEXT);
            }
            keyboardView.setAlt();
        }
    }

    private boolean UpdateNotification(KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes, String notificationText) {
        if(!pref_system_icon_no_notification_text) {
            boolean changed = notificationProcessor.SetSmallIconLayout(iconRes.MipmapResId);
            changed |= notificationProcessor.SetContentTitleLayout(notificationText);
            return changed;
        }
        this.showStatusIcon(iconRes.DrawableResId);
        return true;
    }

    //endregion





    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {
        Log.d(TAG2, "onGetSuggestions");
    }

    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
        Log.d(TAG2, "onGetSentenceSuggestions");
    }


    private boolean IsNavKeyCode(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_Q
                || keyCode == KeyEvent.KEYCODE_A
                || keyCode == KeyEvent.KEYCODE_P
                //LEFT NAV BLOCK
                || keyCode == KeyEvent.KEYCODE_W
                || keyCode == KeyEvent.KEYCODE_E
                || keyCode == KeyEvent.KEYCODE_R
                || keyCode == KeyEvent.KEYCODE_T
                || keyCode == KeyEvent.KEYCODE_S
                || keyCode == KeyEvent.KEYCODE_D
                || keyCode == KeyEvent.KEYCODE_F
                || keyCode == KeyEvent.KEYCODE_G
                //RIGHT BLOCK
                || keyCode == KeyEvent.KEYCODE_Y
                || keyCode == KeyEvent.KEYCODE_U
                || keyCode == KeyEvent.KEYCODE_I
                || keyCode == KeyEvent.KEYCODE_O
                || keyCode == KeyEvent.KEYCODE_H
                || keyCode == KeyEvent.KEYCODE_J
                || keyCode == KeyEvent.KEYCODE_K
                || keyCode == KeyEvent.KEYCODE_L;
    }

    private boolean IsViewModeKeyCode(int keyCode, int meta) {
        if (keyCode == KeyEvent.KEYCODE_0 && (meta & KeyEvent.META_ALT_ON) == 0) return false;
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT) return false;
        if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) return false;
        if (keyCode == KeyEvent.KEYCODE_FUNCTION) return false;
        if(pref_manage_call) {
            if (keyCode == KeyEvent.KEYCODE_SPACE) return false;
            if (keyCode == KeyEvent.KEYCODE_SYM) return false;
        } else {
            if (keyCode == KeyEvent.KEYCODE_SPACE && IsShiftMeta(meta)) return false;
        }
        return true;
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }


    @Override
    protected boolean ProcessOnCursorMovement(EditorInfo editorInfo)
    {
        return DetermineFirstBigCharStateAndUpdateVisualization1(editorInfo);
    }

    protected boolean DetermineFirstBigCharStateAndUpdateVisualization1(EditorInfo editorInfo)
    {
        boolean needUpdateVisual = DetermineFirstBigCharAndReturnChangedState(editorInfo);
        if(needUpdateVisual) {
            UpdateKeyboardModeVisualization();
            return true;
        }
        return false;

    }



    private void playClick(int i) {
/* Пока запиливаем звук, он ругается в дебаге
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch(i){
            case 32:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;

            case Keyboard.KEYCODE_DONE:
            case 10:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;

            case Keyboard.KEYCODE_DELETE:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                break;

            default:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }
*/
    }

    //region MODES/META/RESET

    private void ResetMetaState() {
        doubleAltPressAllSymbolsAlted = false;
        altPressSingleSymbolAltedMode = false;
        oneTimeShiftOneTimeBigMode = false;
        doubleShiftCapsMode = false;
        symPadAltShift = false;
        mode_keyboard_gestures = false;
        mode_keyboard_gestures_plus_up_down = false;
    }




    //endregion

    //region LOAD SETTINGS & KEYBOARDS
    public static ArrayList<KeyboardLayout.KeyboardLayoutOptions> allLayouts;
    KeyoneKb2Settings keyoneKb2Settings;
    private void LoadSettingsAndKeyboards(){

        pref_show_default_onscreen_keyboard = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL);
        pref_keyboard_gestures_at_views_enable = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_9_KEYBOARD_GESTURES_AT_VIEWS_ENABLED);
        pref_gesture_motion_sensitivity = keyoneKb2Settings.GetIntValue(keyoneKb2Settings.APP_PREFERENCES_1_SENS_BOTTOM_BAR);
        pref_show_toast = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_2_SHOW_TOAST);
        pref_manage_call = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_6_MANAGE_CALL);
        pref_alt_space = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_3_ALT_SPACE);
        pref_long_press_key_alt_symbol = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_5_LONG_PRESS_ALT);
        pref_flag = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_4_FLAG);
        pref_height_bottom_bar = keyoneKb2Settings.GetIntValue(keyoneKb2Settings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR);
        pref_system_icon_no_notification_text = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM);

        allLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());
        ArrayList<KeyboardLayout.KeyboardLayoutOptions> activeLayouts = new ArrayList<>();
        //for each keyboard layout in active layouts find in settings and if setting is true then set keyboard layout to active
        for(KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : allLayouts) {
            keyoneKb2Settings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), keyoneKb2Settings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);
            boolean enabled = keyoneKb2Settings.GetBooleanValue(keyboardLayoutOptions.getPreferenceName());
            if(enabled) {
                activeLayouts.add(keyboardLayoutOptions);
            }
        }
        keyboardLayoutManager.Initialize(activeLayouts, getResources(), getApplicationContext());
    }



    //endregion

    boolean IsBitMaskContaining(int bitmask, int bits) {
        return (bitmask & bits) > 0;
    }




    private void ProcessImeOptions() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if(editorInfo != null && editorInfo.inputType != 0 && editorInfo.imeOptions != 0) {
            if(IsBitMaskContaining(editorInfo.imeOptions, EditorInfo.IME_MASK_ACTION)) {
                if(IsBitMaskContaining(editorInfo.imeOptions, EditorInfo.IME_ACTION_DONE)) {
                    //TODO: Доделать
                }
            }
        }
    }

    private boolean IsCtrlPressed(KeyPressData keyPressData) {
        return (keyPressData.MetaBase & (KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON)) > 0;
    }




    private boolean IsNavMode() {
        return keyboardStateFixed_NavModeAndKeyboard || keyboardStateHolding_NavModeAndKeyboard;
    }

    @Override
    protected boolean IsNoGesturesMode() {
        return IsNavMode();
    }

    @Override
    protected boolean IsGestureModeEnabled() {
        return pref_keyboard_gestures_at_views_enable;
    }
    //endregion




}
