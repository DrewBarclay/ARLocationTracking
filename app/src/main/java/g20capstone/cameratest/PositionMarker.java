package g20capstone.cameratest;

/**
 * Created by Drew on 2/16/2017.
 * Code somewhat based on https://github.com/googleglass/gdk-apidemo-sample/blob/master/app/src/main/java/com/google/android/glass/sample/apidemo/opengl/Cube.java
 */

/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class PositionMarker {

    /** Cube vertices */
    private static final float VERTICES[] = {
            -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
            0.5f, 0.5f, -0.5f,
            -0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, 0.5f,
            0.5f, -0.5f, 0.5f,
            0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f
    };

    /** Vertex colors. */
    private static final float COLORS[] = {
            0.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 1.0f, 1.0f, 1.0f,
    };


    /** Order to draw vertices as triangles. */
    private static final byte INDICES[] = {
            0, 1, 3, 3, 1, 2, // Front face.
            0, 1, 4, 4, 5, 1, // Bottom face.
            1, 2, 5, 5, 6, 2, // Right face.
            2, 3, 6, 6, 7, 3, // Top face.
            3, 7, 4, 4, 3, 0, // Left face.
            4, 5, 7, 7, 6, 5, // Rear face.
    };

    /** Number of coordinates per vertex in {@link VERTICES}. */
    private static final int COORDS_PER_VERTEX = 3;

    /** Number of values per colors in {@link COLORS}. */
    private static final int VALUES_PER_COLOR = 4;

    /** Vertex size in bytes. */
    private final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4;

    /** Color size in bytes. */
    private final int COLOR_STRIDE = VALUES_PER_COLOR * 4;

    /** Shader code for the vertex. */
    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vColor;" +
                    "varying vec4 _vColor;" +
                    "void main() {" +
                    "  _vColor = vColor;" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    /** Shader code for the fragment. */
    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
                    "varying vec4 _vColor;" +
                    "void main() {" +
                    "  gl_FragColor = _vColor;" +
                    "}";


    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mColorBuffer;
    private final ByteBuffer mIndexBuffer;
    private final int mProgram;
    private final int mPositionHandle;
    private final int mColorHandle;
    private final int mMVPMatrixHandle;

    public PositionMarker() {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(VERTICES.length * 4);

        byteBuffer.order(ByteOrder.nativeOrder());
        mVertexBuffer = byteBuffer.asFloatBuffer();
        mVertexBuffer.put(VERTICES);
        mVertexBuffer.position(0);

        byteBuffer = ByteBuffer.allocateDirect(COLORS.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        mColorBuffer = byteBuffer.asFloatBuffer();
        mColorBuffer.put(COLORS);
        mColorBuffer.position(0);

        mIndexBuffer = ByteBuffer.allocateDirect(INDICES.length);
        mIndexBuffer.put(INDICES);
        mIndexBuffer.position(0);

        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, ARRenderer.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE));
        GLES20.glAttachShader(
                mProgram, ARRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE));
        GLES20.glLinkProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mColorHandle = GLES20.glGetAttribLocation(mProgram, "vColor");
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
    }

    public void drawAtPosition(float[] vpMatrix, float[] invertedViewMatrix, float x, float y, float z) {
        //Start by calculating inverted view matrix to reverse-rotate the object so that, when rotated, it faces the camera
        //Also translate the object appropriately.
        float[] translationMatrix = new float[16];
        float[] modelMatrix = new float[16];
        float[] mvpMatrix = new float[16];

        Matrix.setIdentityM(translationMatrix, 0);
        Matrix.translateM(translationMatrix, 0, x, y, z);

        Matrix.multiplyMM(modelMatrix, 0, translationMatrix, 0, invertedViewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);

        //Now do the actual drawing with the MVP matrix

        // Add program to OpenGL environment.
        GLES20.glUseProgram(mProgram);

        // Prepare the cube coordinate data.
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(
                mPositionHandle, 3, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mVertexBuffer);

        // Prepare the cube color data.
        GLES20.glEnableVertexAttribArray(mColorHandle);
        GLES20.glVertexAttribPointer(
                mColorHandle, 4, GLES20.GL_FLOAT, false, COLOR_STRIDE, mColorBuffer);

        // Apply the projection and view transformation.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Draw the cube.
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, INDICES.length, GLES20.GL_UNSIGNED_BYTE, mIndexBuffer);

        // Disable vertex arrays.
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mColorHandle);
    }
}