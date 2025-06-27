package com.firstapp.traget_setter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private int screenWidth, screenHeight;
    private TextView  boundaryView;
    private FrameLayout frameLayout,parentLayout;
    private int nextButtonId = 1;
    private PriorityQueue<Integer> availableIds = new PriorityQueue<>();

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int ServerPort = 11199;
    private ExecutorService udpExecutor;
    private volatile boolean isRunning = true;

    private long current;
    private long previous;
    private final long interval = 10000000;  // 10 ms interval

    private final List<Button> addedButtons = new ArrayList<>();
    private int currentSendIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                }
        );
        frameLayout = findViewById(R.id.main);
        boundaryView = findViewById(R.id.boundaryView);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        addAllButton();
        setupAddButtonListener();
        startSendingData();

    }

    // Conversion factors (pre-calculated)
    private final float DP_PER_METER_X = 605f / 15f; // ≈40.33 dp/m (X-axis)
    private final float DP_PER_METER_Y = 385f / 8f;  // ≈48.125 dp/m (Y-axis)

    // Convert real-world meters → dp for positioning
    private float metersToDpX(float meters) { return meters * DP_PER_METER_X; }
    private float metersToDpY(float meters) { return meters * DP_PER_METER_Y; }

    // Convert dp → real-world meters for robot commands
    private float dpToMetersX(float dp) { return dp / DP_PER_METER_X; }
    private float dpToMetersY(float dp) { return dp / DP_PER_METER_Y; }

    @SuppressLint("ClickableViewAccessibility")
    private void setupAddButtonListener() {
        frameLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                addNewButton(x, y);
            }
            return false;
        });
    }


    @SuppressLint("SetTextI18n")
    private static final int MAX_BUTTONS = 27; // Maximum new buttons allowed

    @SuppressLint({"ClickableViewAccessibility", "ResourceAsColor", "DefaultLocale"})
    private void addNewButton(float x, float y) {

        Button newButton = null;
        addedButtons.add(newButton);
        // Ensure boundaryView is initialized
        if (boundaryView == null) {
            boundaryView = findViewById(R.id.boundaryView);
        }

        // Count only the "new buttons" inside boundaryView (excluding main buttons)
        int buttonCount = 0;
        Button deleteButton = new Button(this);
        Button editorButton = new Button(this);
        Button clearButton = new Button(this);
        Button connectButton = new Button(this);
        Button sendButton = new Button(this);
        Button allpointButton = new Button(this);
        Button returnButton = new Button(this);
        Button robotButton = new Button(this);

        for (int i = 0; i < frameLayout.getChildCount(); i++) {
            View child = frameLayout.getChildAt(i);
            if (child instanceof Button && child != robotButton && child != sendButton
                    && child != connectButton && child != editorButton
                    && child != returnButton && child != clearButton && child != allpointButton
                    && child != deleteButton) {
                buttonCount++;
            }
        }

        if (buttonCount >= MAX_BUTTONS) {
            Toast.makeText(this, "Maximum of 20 buttons reached!", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] boundaryLocation = new int[2];
        boundaryView.getLocationOnScreen(boundaryLocation);
        int boundaryLeft = boundaryLocation[0];
        int boundaryTop = boundaryLocation[1];
        int boundaryRight = boundaryView.getWidth();
        int boundaryBottom = boundaryView.getHeight();
        // Calculate button size
        int sizeInPixels = (int) (55 * getResources().getDisplayMetrics().density);

        // Ensure button stays inside boundary
        if (x - sizeInPixels / 2 < boundaryLeft || x + sizeInPixels / 2 > boundaryRight ||
                y - sizeInPixels / 2 < boundaryTop || y + sizeInPixels / 2 > boundaryBottom) {
            Toast.makeText(this, "Cannot add button outside the boundary area.", Toast.LENGTH_SHORT).show();
            return;
        }
        newButton = new Button(this);
        int buttonId = availableIds.isEmpty() ? nextButtonId++ : availableIds.poll();

        // Normalize X and Y to fit within 0-120 and 0-240 range
        float normalizedX = ((x - boundaryLeft) / boundaryRight) * 120f;
        float normalizedY = ((y - boundaryTop) / boundaryBottom) * 240f;

        normalizedX = (float) Math.max(0.0f, Math.min(normalizedX, 120.0f));
        normalizedY = (float) Math.max(0.0f, Math.min(normalizedY, 240.0f));

        // Inside addNewButton(), after calculating normalizedX and normalizedY:
        float realX = (normalizedX / 120f) * 15f;  // Convert to meters2
        float realY = (normalizedY / 240f) * 8f;

        newButton.setText(String.format("ID: %d\nX: %.3f\nY: %.3f", buttonId, normalizedX, normalizedY));
        newButton.setTextSize(8);
//        newButton.setTextColor();

        newButton.setTag(buttonId);
        newButton.setBackgroundResource(R.drawable.circle_button);
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", buttonId);
            jsonObject.put("x", normalizedX);
            jsonObject.put("y", normalizedY);

            sendUdpData(jsonObject.toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        float actualX = (normalizedX / 120) * boundaryRight + boundaryLeft;
        float actualY = (normalizedY / 240) * boundaryBottom + boundaryTop;

        // Convert absolute position to relative position inside frameLayout
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        params.leftMargin = (int) (actualX - boundaryLeft - sizeInPixels / 2);
        params.topMargin = (int) (actualY - boundaryTop - sizeInPixels / 2);
        newButton.setLayoutParams(params);
        newButton.setOnTouchListener(new DragTouchListener());
        newButton.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private int lastAction;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        lastAction = MotionEvent.ACTION_DOWN;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;

                        // Ensure new position is within boundary
                        float adjustedX = (float) Math.max(boundaryLeft, Math.min(newX, boundaryRight - v.getWidth()));
                        float adjustedY = (float) Math.max(boundaryTop, Math.min(newY, boundaryBottom - v.getHeight()));

                        v.setX(adjustedX);
                        v.setY(adjustedY);

                        // Update display with new normalized X and Y
                        float updatedX = ((adjustedX - boundaryLeft) / (float) boundaryView.getWidth()) * 120;
                        float updatedY = ((adjustedY - boundaryTop) / (float) boundaryView.getHeight()) * 240;
                        ((Button) v).setText("ID: " + buttonId + String.format("\nX:%.3f \nY:%.3f",
                                updatedX,
                                updatedY));

                        lastAction = MotionEvent.ACTION_MOVE;
                        break;

                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            v.performClick();
                        }
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
        frameLayout.addView(newButton);
        addedButtons.add(newButton);
    }

    private void addAllButton() {

        Button deleteButton = new Button(this);
        Button editorButton = new Button(this);
        Button clearButton = new Button(this);
        Button connectButton = new Button(this);
        Button sendButton = new Button(this);
        Button returnButton = new Button(this);
        Button robotButton = new Button(this);
        Button allpointButton = new Button(this);

        deleteButton.setText("DEL");
        deleteButton.setTextSize(10);
        deleteButton.setBackgroundResource(R.drawable.delete_button);

        int sizeInPixels = (int) (40 * getResources().getDisplayMetrics().density);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        params.gravity = Gravity.END | Gravity.TOP;
        params.rightMargin = 50;
        deleteButton.setLayoutParams(params);
        int spacss= 290;
        params.topMargin  = sizeInPixels + spacss;

        deleteButton.setOnClickListener(v -> {
            boolean hasDeletableButtons = false;

            // Check if there are any deletable buttons
            for (int i = 0; i < frameLayout.getChildCount(); i++) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button  && child != robotButton && child != deleteButton && child != editorButton && child != clearButton && child != boundaryView  && child != sendButton
                        && child != returnButton && child != connectButton && child != allpointButton ) {
                    hasDeletableButtons = true;
                    break;
                }
            }
            if (!hasDeletableButtons) {
                Toast.makeText(this, "No buttons left to delete!", Toast.LENGTH_SHORT).show();
                return;
            }
            // Remove the last dynamically added button
            for (int i = frameLayout.getChildCount() - 1; i >= 0; i--) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button  && child != robotButton && child != deleteButton && child != editorButton && child != clearButton && child != boundaryView  && child != sendButton
                        && child != returnButton && child != connectButton && child != allpointButton) {
                    frameLayout.removeView(child);
                    Integer buttonId = (Integer) child.getTag();
                    if (buttonId != null) {
                        availableIds.add(buttonId);
                    }
                    return; // Only remove one button at a time
                }
            }
                // ✅ Clear send queue and reset index
            currentSendIndex--;
            addedButtons.clear();

            Toast.makeText(this, "All buttons and send queue cleared!", Toast.LENGTH_SHORT).show();
        });

        editorButton.setText("EDIT");
        editorButton.setTextSize(10);
        editorButton.setBackgroundResource(R.drawable.editorbtn);

        FrameLayout.LayoutParams editorButtonParams = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        editorButtonParams.gravity = Gravity.END | Gravity.TOP;
        editorButtonParams.rightMargin = 50;
        int spacing = 60; // Space between the "Delete" and "Edit" buttons in pixels
        editorButtonParams.topMargin = sizeInPixels + spacing;
        editorButton.setLayoutParams(editorButtonParams);
        editorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean hasEditableButton = false;
                // Implement your edit functionality here
                Toast.makeText(MainActivity.this, "Edit button clicked", Toast.LENGTH_SHORT).show();

                // Loop through children to check for editable buttons
                for (int i = 0; i < frameLayout.getChildCount(); i++) {
                    View child = frameLayout.getChildAt(i);
                    if (child instanceof Button && child != editorButton && child != deleteButton && child != clearButton) {
                        hasEditableButton = true;
                        break;
                    }
                }
                if (!hasEditableButton) {
                    Toast.makeText(MainActivity.this, "No buttons to edit!", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Proceed with opening the editor interface
                showEditDialog();
            }

        });
        editorButton.setOnClickListener(v -> showEditDialog());

        clearButton.setText("CLEAR");
        clearButton.setTextSize(10);
        clearButton.setBackgroundResource(R.drawable.clearbtn);

        FrameLayout.LayoutParams clearButtonParams = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        clearButtonParams.gravity = Gravity.END | Gravity.TOP;
        clearButtonParams.rightMargin = 50;

        int spacings =170;
        clearButtonParams.topMargin = sizeInPixels + spacings;
        clearButton.setLayoutParams(clearButtonParams);

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Implement your edit functionality here
                Toast.makeText(MainActivity.this, "Clear ALL", Toast.LENGTH_SHORT).show();
            }
        });
        clearButton.setOnClickListener(v -> {
            boolean hasButtonsToClear = false;

            // Check if there are any removable buttons
            for (int i = 0; i < frameLayout.getChildCount(); i++) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button && child != robotButton && child != deleteButton && child != editorButton && child != clearButton && child != boundaryView  && child != sendButton
                        && child != returnButton && child != connectButton && child != allpointButton)  {
                    hasButtonsToClear = true;
                    break;
                }
            }
            if (!hasButtonsToClear) {
                Toast.makeText(this, "No buttons left to clear!", Toast.LENGTH_SHORT).show();
                return;
            }
            // Remove all dynamically added buttons
            for (int i = frameLayout.getChildCount() - 1; i >= 0; i--) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button && child != robotButton && child != deleteButton && child != editorButton && child != clearButton && child != boundaryView  && child != sendButton
                        && child != returnButton && child != connectButton  && child != allpointButton)  {
                    frameLayout.removeView(child);
                    Integer buttonId = (Integer) child.getTag();
                    if (buttonId != null) {
                        availableIds.add(buttonId);
                    }
                }
            }
            // ✅ Clear send queue and reset index
            addedButtons.clear();
            currentSendIndex = 0;

            Toast.makeText(this, "All buttons and send queue cleared!", Toast.LENGTH_SHORT).show();
        });



        connectButton.setText("CONIP");
        connectButton.setTextSize(10);
        connectButton.setBackgroundResource(R.drawable.ipbtn);
        FrameLayout.LayoutParams connectButtonParams = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        connectButtonParams.gravity = Gravity.END | Gravity.TOP;
        connectButtonParams.rightMargin = 50;
        connectButtonParams.topMargin  = 50;
        connectButton.setLayoutParams(connectButtonParams);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an AlertDialog for input
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Enter IP Address & Port");

                // Create a Layout for the dialog
                LinearLayout layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(50, 20, 50, 20);

                // Input field for IP Address
                EditText ipAddressInput = new EditText(MainActivity.this);
                ipAddressInput.setHint("Enter IP Address");
                ipAddressInput.setInputType(InputType.TYPE_CLASS_TEXT);

                // Input field for Port
                EditText portInput = new EditText(MainActivity.this);
                portInput.setHint("Enter Port");
                portInput.setInputType(InputType.TYPE_CLASS_NUMBER);

                // Add inputs to layout
                layout.addView(ipAddressInput);
                layout.addView(portInput);
                builder.setView(layout);

                // "Connect" button
                builder.setPositiveButton("Connect", (dialog, which) -> {
                    String ipAddress = ipAddressInput.getText().toString().trim();
                    String portString = portInput.getText().toString().trim();

                    if (ipAddress.isEmpty() || portString.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Please enter IP and Port!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int port;
                    try {
                        port = Integer.parseInt(portString);
                    } catch (NumberFormatException e) {
                        Toast.makeText(MainActivity.this, "Invalid port number!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        serverAddress = InetAddress.getByName(ipAddress);
                        ServerPort = port;

                        // Create a single socket for the session after connecting
                        if (socket != null && !socket.isClosed()) {
                            socket.close();
                        }
                        socket = new DatagramSocket(); // opens UDP port on device
                        socket.setBroadcast(true);

                        Toast.makeText(MainActivity.this, "Connected to " + ipAddress + ":" + port, Toast.LENGTH_SHORT).show();

                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Failed to set up connection: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                builder.show();
            }
        });

        sendButton.setText("NTP");
        sendButton.setTextSize(10);
        sendButton.setBackgroundResource(R.drawable.sendbtn);

        FrameLayout.LayoutParams sendButtonParams = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        sendButtonParams.gravity = Gravity.END | Gravity.TOP;
        sendButtonParams.rightMargin = 50;

        int spacingsss = 430;
        sendButtonParams.topMargin = sizeInPixels + spacingsss;
        sendButton.setLayoutParams(sendButtonParams);

        sendButton.setOnClickListener(v -> {
            JSONArray jsonArray = new JSONArray();

            // First get the boundary view's position and dimensions
            int[] boundaryLocation = new int[2];
            boundaryView.getLocationOnScreen(boundaryLocation);
            int boundaryLeft = boundaryLocation[0];
            int boundaryTop = boundaryLocation[1];
            int boundaryWidth = boundaryView.getWidth();
            int boundaryHeight = boundaryView.getHeight();

            for (int i = 0; i < frameLayout.getChildCount(); i++) {
                View child = frameLayout.getChildAt(i);

                if (child instanceof Button && child != robotButton && child != deleteButton
                        && child != editorButton && child != clearButton && child != boundaryView
                        && child != sendButton && child != returnButton && child != connectButton
                        && child != allpointButton) {

                    Button targetButton = (Button) child;
                    if (targetButton.getTag() == null) continue;

                    try {
                        int buttonId = (int) targetButton.getTag();

                        // Extract displayed values from button text
                        String buttonText = targetButton.getText().toString();
                        String[] lines = buttonText.split("\n");

                        // Parse X and Y values directly from button text to ensure consistency
                        float normalizedX = 0f;
                        float normalizedY = 0f;

                        // Parse X and Y from the button text which has format "ID: n\nX: xx.xxx\nY: yy.yyy"
                        if (lines.length >= 3) {
                            try {
                                String xText = lines[1].substring(lines[1].indexOf(":") + 1).trim();
                                String yText = lines[2].substring(lines[2].indexOf(":") + 1).trim();
                                normalizedX = Float.parseFloat(xText);
                                normalizedY = Float.parseFloat(yText);
                            } catch (Exception e) {
                                // If parsing from text fails, calculate from position
                                int[] buttonLocation = new int[2];
                                targetButton.getLocationOnScreen(buttonLocation);
                                float buttonX = buttonLocation[0] + (targetButton.getWidth() / 2);
                                float buttonY = buttonLocation[1] + (targetButton.getHeight() / 2);

                                normalizedX = ((buttonX - boundaryLeft) / (float)boundaryWidth) * 120f;
                                normalizedY = ((buttonY - boundaryTop) / (float)boundaryHeight) * 240f;

                                normalizedX = Math.max(0.0f, Math.min(normalizedX, 120.0f));
                                normalizedY = Math.max(0.0f, Math.min(normalizedY, 240.0f));

                                normalizedX = Float.parseFloat(String.format(Locale.US, "%.3f", normalizedX));
                                normalizedY = Float.parseFloat(String.format(Locale.US, "%.3f", normalizedY));
                            }
                        }

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("id", buttonId);
                        jsonObject.put("x", normalizedX);
                        jsonObject.put("y", normalizedY);

                        jsonArray.put(jsonObject);

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error sending button data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            if (jsonArray.length() > 0) {
                // Check which format to use based on your UDP receiver expectations
                try {

                     JSONObject dataContainer = new JSONObject();
                     dataContainer.put("points", jsonArray);

                     sendUdpData(dataContainer.toString());

                    Toast.makeText(MainActivity.this, "Sent " + jsonArray.length() + " buttons' data!", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Error creating JSON data", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "No buttons to send!", Toast.LENGTH_SHORT).show();
            }
        });



        allpointButton.setText("ATP");
        allpointButton.setTextSize(10);
        allpointButton.setBackgroundResource(R.drawable.all_in_point);
        FrameLayout.LayoutParams allButtonParams = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        allButtonParams.gravity = Gravity.END | Gravity.TOP;
        allButtonParams.rightMargin = 50;
        int spacingz = 580;
        allButtonParams.topMargin = sizeInPixels + spacingz;
        allpointButton.setLayoutParams(allButtonParams);

        // Fix the allpointButton click listener to send all points at once
        allpointButton.setOnClickListener(v -> {
            // Create a JSON array of all button data
            JSONArray allPointsArray = new JSONArray();

            // First get the boundary view's position and dimensions
            int[] boundaryLocation = new int[2];
            boundaryView.getLocationOnScreen(boundaryLocation);
            int boundaryLeft = boundaryLocation[0];
            int boundaryTop = boundaryLocation[1];
            int boundaryWidth = boundaryView.getWidth();
            int boundaryHeight = boundaryView.getHeight();

            // Collect all buttons in the frameLayout
            for (int i = 0; i < frameLayout.getChildCount(); i++) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button && child != robotButton && child != deleteButton
                        && child != editorButton && child != clearButton && child != boundaryView
                        && child != sendButton && child != returnButton && child != connectButton
                        && child != allpointButton) {

                    Button targetButton = (Button) child;
                    if (targetButton.getTag() == null) continue;

                    try {
                        int buttonId = (int) targetButton.getTag();

                        // Extract displayed values from button text
                        String buttonText = targetButton.getText().toString();
                        String[] lines = buttonText.split("\n");

                        // Parse X and Y values directly from button text to ensure consistency
                        float normalizedX = 0f;
                        float normalizedY = 0f;

                        // Parse X and Y from the button text which has format "ID: n\nX: xx.xxx\nY: yy.yyy"
                        if (lines.length >= 3) {
                            try {
                                String xText = lines[1].substring(lines[1].indexOf(":") + 1).trim();
                                String yText = lines[2].substring(lines[2].indexOf(":") + 1).trim();
                                normalizedX = Float.parseFloat(xText);
                                normalizedY = Float.parseFloat(yText);
                            } catch (Exception e) {
                                // If parsing from text fails, calculate from position
                                int[] buttonLocation = new int[2];
                                targetButton.getLocationOnScreen(buttonLocation);
                                float buttonX = buttonLocation[0] + (targetButton.getWidth() / 2);
                                float buttonY = buttonLocation[1] + (targetButton.getHeight() / 2);

                                normalizedX = ((buttonX - boundaryLeft) / (float)boundaryWidth) * 120f;
                                normalizedY = ((buttonY - boundaryTop) / (float)boundaryHeight) * 240f;

                                normalizedX = Math.max(0.0f, Math.min(normalizedX, 120.0f));
                                normalizedY = Math.max(0.0f, Math.min(normalizedY, 240.0f));

                                normalizedX = Float.parseFloat(String.format(Locale.US, "%.3f", normalizedX));
                                normalizedY = Float.parseFloat(String.format(Locale.US, "%.3f", normalizedY));
                            }
                        }

                        JSONObject pointObj = new JSONObject();
                        pointObj.put("id", buttonId);
                        pointObj.put("x", normalizedX);
                        pointObj.put("y", normalizedY);

                        allPointsArray.put(pointObj);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error processing button data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            if (allPointsArray.length() > 0) {
                try {
                    // Create a container object for all points
                    JSONObject allPointsContainer = new JSONObject();
                    allPointsContainer.put("all_points", allPointsArray);

                    // Send the data
                    sendUdpData(allPointsContainer.toString());
                    Toast.makeText(MainActivity.this, "Sent all " + allPointsArray.length() + " points!", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Error creating JSON data", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "No buttons to send!", Toast.LENGTH_SHORT).show();
            }
        });



        returnButton.setText("RETURN");
        returnButton.setTextSize(8);
        returnButton.setBackgroundResource(R.drawable.returnbtn);
        FrameLayout.LayoutParams returnButtonParams = new FrameLayout.LayoutParams(sizeInPixels, sizeInPixels);
        returnButtonParams.gravity = Gravity.END | Gravity.TOP;
        returnButtonParams.rightMargin = 50;
        int spacingssss = 710;
        returnButtonParams.topMargin = sizeInPixels + spacingssss;
        returnButton.setLayoutParams(returnButtonParams);
        returnButton.setOnClickListener(v -> {
            // Create arrays to hold buttons and their data
            List<Button> allButtons = new ArrayList<>();

            // First get the boundary view's position and dimensions
            int[] boundaryLocation = new int[2];
            boundaryView.getLocationOnScreen(boundaryLocation);
            int boundaryLeft = boundaryLocation[0];
            int boundaryTop = boundaryLocation[1];
            int boundaryWidth = boundaryView.getWidth();
            int boundaryHeight = boundaryView.getHeight();

            // Collect all buttons in the frameLayout
            for (int i = 0; i < frameLayout.getChildCount(); i++) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button && child != robotButton && child != deleteButton
                        && child != editorButton && child != clearButton && child != boundaryView
                        && child != sendButton && child != returnButton && child != connectButton
                        && child != allpointButton) {

                    allButtons.add((Button)child);
                }
            }

            if (allButtons.isEmpty()) {
                Toast.makeText(MainActivity.this, "No buttons to reverse!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Reverse the order of the buttons in the list
            Collections.reverse(allButtons);

            // Create JSON array with reversed data
            try {
                JSONArray reversedArray = new JSONArray();

                for (Button targetButton : allButtons) {
                    if (targetButton.getTag() == null) continue;

                    try {
                        int buttonId = (int) targetButton.getTag();

                        // Extract displayed values from button text
                        String buttonText = targetButton.getText().toString();
                        String[] lines = buttonText.split("\n");

                        // Parse X and Y values directly from button text to ensure consistency
                        float normalizedX = 0f;
                        float normalizedY = 0f;

                        // Parse X and Y from the button text which has format "ID: n\nX: xx.xxx\nY: yy.yyy"
                        if (lines.length >= 3) {
                            try {
                                String xText = lines[1].substring(lines[1].indexOf(":") + 1).trim();
                                String yText = lines[2].substring(lines[2].indexOf(":") + 1).trim();
                                normalizedX = Float.parseFloat(xText);
                                normalizedY = Float.parseFloat(yText);
                            } catch (Exception e) {
                                // If parsing from text fails, calculate from position
                                int[] buttonLocation = new int[2];
                                targetButton.getLocationOnScreen(buttonLocation);
                                float buttonX = buttonLocation[0] + (targetButton.getWidth() / 2);
                                float buttonY = buttonLocation[1] + (targetButton.getHeight() / 2);

                                normalizedX = ((buttonX - boundaryLeft) / (float)boundaryWidth) * 120f;
                                normalizedY = ((buttonY - boundaryTop) / (float)boundaryHeight) * 240f;

                                normalizedX = Math.max(0.0f, Math.min(normalizedX, 120.0f));
                                normalizedY = Math.max(0.0f, Math.min(normalizedY, 240.0f));

                                normalizedX = Float.parseFloat(String.format(Locale.US, "%.3f", normalizedX));
                                normalizedY = Float.parseFloat(String.format(Locale.US, "%.3f", normalizedY));
                            }
                        }

                        JSONObject pointObj = new JSONObject();
                        pointObj.put("id", buttonId);
                        pointObj.put("x", normalizedX);
                        pointObj.put("y", normalizedY);

                        reversedArray.put(pointObj);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error processing button data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                if (reversedArray.length() > 0) {
                    // Create container object for reversed points
                    JSONObject reversedContainer = new JSONObject();
                    reversedContainer.put("reversed_points", reversedArray);

                    // Send the reversed data
                    sendUdpData(reversedContainer.toString());

                    // Update the addedButtons list if you're still using it elsewhere in your code
                    addedButtons.clear();
                    addedButtons.addAll(allButtons);

                    // Reset the send index if you're still using it elsewhere
//                    if (currentSendIndex !=  null) {
//                        currentSendIndex = 0;
//                    }

                    Toast.makeText(MainActivity.this, "Button order reversed and sent! (" + reversedArray.length() + " buttons)", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "No valid buttons to reverse!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error sending reversed data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        //        robotButton.setBackgroundResource(R.drawable.robotbtn);
////        FrameLayout.LayoutParams robotParams = new FrameLayout.LayoutParams(sizeInPixels-20, sizeInPixels-20);
////        robotParams.gravity = Gravity.START | Gravity.TOP;
////        robotParams.leftMargin = (int) robotX;
////        robotParams.topMargin = (int) robotY + 100;
////        robotButton.setLayoutParams(robotParams);
//        frameLayout.addView(robotButton);

        frameLayout.addView(deleteButton);
        frameLayout.addView(editorButton);
        frameLayout.addView(clearButton);
        frameLayout.addView(connectButton);
        frameLayout.addView(sendButton);
        frameLayout.addView(returnButton);
        frameLayout.addView(allpointButton);
    }


    private class DragTouchListener implements View.OnTouchListener {
        private float startX, startY;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    v.setX(v.getX() + dx);
                    v.setY(v.getY() + dy);
                    startX = event.getRawX();
                    startY = event.getRawY();
                    return true;
            }
            return false;
        }
    }

    // Function to show the edit dialog
    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Button");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        EditText inputId = new EditText(this);
        inputId.setHint("Enter Button ID");

        EditText inputX = new EditText(this);
        inputX.setHint("Enter X Position (0-120)");

        EditText inputY = new EditText(this);
        inputY.setHint("Enter Y Position (0-240)");

        Button deleteButton = new Button(this);
        deleteButton.setText("Clear Selected Button");
        deleteButton.setBackgroundColor(Color.RED);
        deleteButton.setTextColor(Color.WHITE);

        layout.addView(inputId);
        layout.addView(inputX);
        layout.addView(inputY);
        layout.addView(deleteButton);

        builder.setView(layout);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String idText = inputId.getText().toString();
            String xText = inputX.getText().toString();
            String yText = inputY.getText().toString();

            if (idText.isEmpty() || xText.isEmpty() || yText.isEmpty()) {
                Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show();
                return;
            }
            int buttonId;
            float newX, newY;
            try {
                buttonId = Integer.parseInt(idText);
                newX = Float.parseFloat(xText);
                newY = Float.parseFloat(yText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid input format!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate X and Y values
            if (newX < 0 || newX > 120 || newY < 0 || newY > 240) {
                Toast.makeText(this, "X must be between 0-120 and Y between 0-240!", Toast.LENGTH_SHORT).show();
                return;
            }
            for (int i = 0; i < frameLayout.getChildCount(); i++) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button && child.getTag() != null) {
                    Integer tagId = (Integer) child.getTag();
                    if (tagId != null && tagId == buttonId) {
                        // Convert normalized X and Y to actual pixel values
                        int[] boundaryLocation = new int[2];
                        boundaryView.getLocationOnScreen(boundaryLocation);
                        int boundaryLeft = boundaryLocation[0];
                        int boundaryTop = boundaryLocation[1];
                        int boundaryWidth = boundaryView.getWidth();
                        int boundaryHeight = boundaryView.getHeight();

                        // Calculate actual pixel positions
                        float actualX = (newX / 120) * boundaryWidth + boundaryLeft;
                        float actualY = (newY / 240) * boundaryHeight + boundaryTop;

                        // Update button position
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
                        params.leftMargin = (int) (actualX - boundaryLeft - (float) child.getWidth() / 2);
                        params.topMargin = (int) (actualY - boundaryTop - (float) child.getHeight() / 2);
                        child.setLayoutParams(params);

                        // Update button text with normalized values
                        ((Button) child).setText("ID: " + buttonId + "\nX: " + (int) newX + "\nY: " + (int) newY);

                        Toast.makeText(this, "Button updated!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            Toast.makeText(this, "Button ID not found!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        deleteButton.setOnClickListener(v -> {
            String idText = inputId.getText().toString();
            if (idText.isEmpty()) {
                Toast.makeText(this, "Enter a valid Button ID!", Toast.LENGTH_SHORT).show();
                return;
            }

            int buttonId;
            try {
                buttonId = Integer.parseInt(idText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid ID format!", Toast.LENGTH_SHORT).show();
                return;
            }

            for (int i = 0; i < frameLayout.getChildCount(); i++) {
                View child = frameLayout.getChildAt(i);
                if (child instanceof Button && child.getTag() != null) {
                    Integer tagId = (Integer) child.getTag();
                    if (tagId != null && tagId == buttonId) {
                        frameLayout.removeView(child);
                        availableIds.add(buttonId);
                        Toast.makeText(this, "Button deleted!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        return;
                    }
                }
            }

            Toast.makeText(this, "Button ID not found!", Toast.LENGTH_SHORT).show();
        });
    }
    private void startSendingData() {
        udpExecutor = Executors.newSingleThreadExecutor();
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true); // Enable broadcasting if necessary
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to initialize socket", Toast.LENGTH_SHORT).show();
            return;
        }
        udpExecutor.execute(() -> {
            while (isRunning) {
                current = System.nanoTime();
                if (current - previous >= interval) {
                    previous = current;
                }
            }
        });
    }

    private void sendUdpData(String message) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                byte[] dataToSend = message.getBytes();
                DatagramPacket packet = new DatagramPacket(dataToSend, dataToSend.length, serverAddress, ServerPort);
                socket.send(packet);
                runOnUiThread(() -> Toast.makeText(this, "UDP data sent successfully!", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to send UDP data", Toast.LENGTH_SHORT).show());
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        });
    }
    @Override
    protected void onDestroy() {
        isRunning = false;
        super.onDestroy();
        if (udpExecutor != null)
            udpExecutor.shutdown();
        if (socket != null && socket.isClosed())
            socket.close();
    }
//    private float dpToMetersY(float dp) { return dp / DP_PER_METER_Y; }

    /**
     * Calculates normalized coordinates for a given button within the boundary view
     * @param targetButton The button to calculate coordinates for
     * @param boundaryView The reference boundary view
     * @return A float array where [0] is normalized X, [1] is normalized Y
     */
    private float[] calculateNormalizedCoordinates(Button targetButton, TextView boundaryView) {
        // Get boundaryView's position and size first
        int[] boundaryLocation = new int[2];
        boundaryView.getLocationOnScreen(boundaryLocation);
        int boundaryLeft = boundaryLocation[0];
        int boundaryTop = boundaryLocation[1];
        int boundaryWidth = boundaryView.getWidth();
        int boundaryHeight = boundaryView.getHeight();

        // Calculate button's absolute screen position
        int[] buttonLocation = new int[2];
        targetButton.getLocationOnScreen(buttonLocation);

        // Calculate button center
        float buttonCenterX = buttonLocation[0] + targetButton.getWidth() / 2f;
        float buttonCenterY = buttonLocation[1] + targetButton.getHeight() / 2f;

        // Normalize coordinates
        float normalizedX = ((buttonCenterX - boundaryLeft) / boundaryWidth) * 120f;
        float normalizedY = ((buttonCenterY - boundaryTop) / boundaryHeight) * 240f;

        // Ensure normalized values are within range
        normalizedX = (float) Math.max(0.0f, Math.min(normalizedX, 120.0f));
        normalizedY = (float) Math.max(0.0f, Math.min(normalizedY, 240.0f));

        // Format to 3 decimal places for consistency
        normalizedX = Float.parseFloat(String.format("%.3f", normalizedX));
        normalizedY = Float.parseFloat(String.format("%.3f", normalizedY));

        return new float[]{normalizedX, normalizedY};
    }
    @SuppressLint("ClickableViewAccessibility")
    private void getLocation() {
        frameLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float buttonX = event.getX();
                float buttonY = event.getY();
            }
            return false;
        });
    }
}

