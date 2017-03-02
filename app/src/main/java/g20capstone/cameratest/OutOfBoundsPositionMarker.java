package g20capstone.cameratest;

/**
 * Created by Drew on 3/02/2017.
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

public class OutOfBoundsPositionMarker {
    // S, T (or X, Y)
    // Texture coordinate data.
    // Because images have a Y axis pointing downward (values increase as you move down the image) while
    // OpenGL has a Y axis pointing upward, we adjust for that here by flipping the Y axis.
    // What's more is that the texture coordinates are the same for every face.
    private static final float[] TEXTURE_COORDINATES = {
            0.0f, 1.0f, //bottom left of the square
            1.0f, 1.0f, //bottom right
            1.0f, 0.0f,
            0.0f, 0.0f
    };

    /** Cube vertices */
    private static final float VERTICES[] = {
            -0.5f, -0.5f, 0, //bottom left - 0
            0.5f, -0.5f, 0, //bottom right - 1
            0.5f, 0.5f, 0, //top right - 2
            -0.5f, 0.5f, 0 //top left - 3
    };

    /** Vertex colors. */
    private static final float COLORS[] = {
            0.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f
    };


    /** Order to draw vertices as triangles. */
    private static final byte INDICES[] = {
            0, 1, 3, //triangle, ccw, bottom left
            1, 2, 3 //triangle, ccw, top right of it
    };

    /** Number of coordinates per vertex in {@link VERTICES}. */
    private static final int COORDS_PER_VERTEX = 3;

    /** Number of values per colors in {@link COLORS}. */
    private static final int VALUES_PER_COLOR = 4;

    /** Vertex size in bytes. */
    private final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4;

    /** Color size in bytes. */
    private final int COLOR_STRIDE = VALUES_PER_COLOR * 4;

    private final int TEXTURE_COORDINATE_SIZE = 2;

    /** Shader code for the vertex. */
    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec4 vColor;" +
            "attribute vec2 a_TexCoordinate;" +
            "varying vec4 _vColor;" +
            "varying vec2 v_TexCoordinate;" +
            "void main() {" +
            "  _vColor = vColor;" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  v_TexCoordinate = a_TexCoordinate;" +
            "}";

    /** Shader code for the fragment. */
    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
            "uniform sampler2D u_Texture;" +
            "varying vec2 v_TexCoordinate;" +
            "varying vec4 _vColor;" +
            "void main() {" +
            "  gl_FragColor = texture2D(u_Texture, v_TexCoordinate);" +
            "}";

    private int mTextureDataHandle;

    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mColorBuffer;
    private final FloatBuffer mTextureCoordinates;
    private final ByteBuffer mIndexBuffer;
    private final int mProgram;
    private final int mPositionHandle;
    private final int mColorHandle;
    private final int mMVPMatrixHandle;
    private int mTextureUniformHandle;
    private final int mTextureCoordinateHandle;

    public OutOfBoundsPositionMarker(int textureHandle) {
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

        byteBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDINATES.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        mTextureCoordinates = byteBuffer.asFloatBuffer();
        mTextureCoordinates.put(TEXTURE_COORDINATES);
        mTextureCoordinates.position(0);

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
        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgram, "u_Texture");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate");

        mTextureDataHandle = textureHandle;
    }

    //x y given in NDC
    public void drawAtPosition(float[] vpMatrix, float[] invertedVPMatrix, float[] invertedViewMatrix, float x, float y) {
        //Find border position
        //Find where [x, y] intersects with the NDC box (ignore Z component).  Whichever component is larger dictates this.
        //If we divide by the absolute value of the larger component, we will make our vector land right on the edge of the NCD box
        //(which is to say one of x and y will be = 1).
        float divisor = Math.max(Math.abs(x), Math.abs(y));
        //TODO proper z value that is not -0.98
        float[] edgeVector = {x/divisor, y/divisor, 0.25f, 1}; //homogenous component set to 1

        //Rotate based on vector in NDC, then rotate on inverted view matrix so it faces the camera
        float[] rotateToFaceEdgeMatrix = new float[16];
        Matrix.setIdentityM(rotateToFaceEdgeMatrix, 0);
        float angle = (float)Math.atan2(edgeVector[1], edgeVector[0]);
        Matrix.rotateM(rotateToFaceEdgeMatrix, 0, angle*180f/(float)Math.PI, 0, 0, 1f);
        Matrix.translateM(rotateToFaceEdgeMatrix, 0, -0.5f, 0, 0); //correct so the right edge is on the right border

        //Face camera...
        float[] rotationMatrix = new float[16];
        Matrix.multiplyMM(rotationMatrix, 0, invertedViewMatrix, 0, rotateToFaceEdgeMatrix, 0);

        //Convert from NDC to world coordinates via inverted VP matrix, translate matrix to that
        float[] worldCoords = new float[4];
        Matrix.multiplyMV(worldCoords, 0, invertedVPMatrix, 0, edgeVector, 0);
        Math3D.fixHomogenous(worldCoords); //w may be non-1

        float[] translationMatrix = new float[16];
        Matrix.setIdentityM(translationMatrix, 0);
        Matrix.translateM(translationMatrix, 0, worldCoords[0], worldCoords[1], worldCoords[2]);

        float[] modelMatrix = new float[16];
        Matrix.multiplyMM(modelMatrix, 0, translationMatrix, 0, rotationMatrix, 0);

        //Draw with mvp matrix.
        float[] mvpMatrix = new float[16];
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

        // Pass in the texture coordinate information
        mTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, TEXTURE_COORDINATE_SIZE, GLES20.GL_FLOAT, false,
                0, mTextureCoordinates);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // Apply the projection and view transformation.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle);

        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        // Draw the cube.
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, INDICES.length, GLES20.GL_UNSIGNED_BYTE, mIndexBuffer);

        // Disable vertex arrays.
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mColorHandle);
    }
}