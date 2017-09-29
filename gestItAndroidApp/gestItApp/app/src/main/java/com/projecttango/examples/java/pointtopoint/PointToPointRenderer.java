/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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
package com.projecttango.examples.java.pointtopoint;

import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.Renderer;

import org.rajawali3d.primitives.Sphere;

import java.util.ArrayList;
import java.util.Stack;

import javax.microedition.khronos.opengles.GL10;

import com.projecttango.tangosupport.TangoSupport;

/**
 * Very simple example point-to-point renderer which displays a line fixed in place.
 * When the user clicks the screen, the line is re-rendered with an endpoint
 * placed at the point corresponding to the depth at the point of the click.
 */
public class PointToPointRenderer extends Renderer {
    private static final String TAG = PointToPointRenderer.class.getSimpleName();

    private float[] textureCoords0 = new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    private Object3D mLine;
    private Stack<Vector3> mPoints;
    private boolean mLineUpdated = false;

    // Augmented reality related fields.
    private ATexture mTangoCameraTexture;
    private boolean mSceneCameraConfigured;
    private ScreenQuad mBackgroundQuad;

    private Matrix4 mObjectTransform;
    private boolean mObjectPoseUpdated = false;
    private static final Matrix4 DEPTH_T_OPENGL = new Matrix4(new float[] {
            1.0f,  0.0f, 0.0f, 0.0f,
            0.0f,  0.0f, 1.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f,  0.0f, 0.0f, 1.0f
    });

    public PointToPointRenderer(Context context) {
        super(context);
    }

    ArrayList<Vector3> allAppCord = new ArrayList<Vector3>();

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        if (mBackgroundQuad == null) {
            mBackgroundQuad = new ScreenQuad();
            mBackgroundQuad.getGeometry().setTextureCoords(textureCoords0);
        }
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);

        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering.
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            mBackgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(mBackgroundQuad, 0);

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);
    }

    /**
     * Update background texture's UV coordinates when device orientation is changed (i.e., change
     * between landscape and portrait mode).
     * This must be run in the OpenGL thread.
     */
    public void updateColorCameraTextureUvGlThread(int rotation) {
        if (mBackgroundQuad == null) {
            mBackgroundQuad = new ScreenQuad();
        }

        float[] textureCoords =
                TangoSupport.getVideoOverlayUVBasedOnDisplayRotation(textureCoords0, rotation);
        mBackgroundQuad.getGeometry().setTextureCoords(textureCoords, true);
        mBackgroundQuad.getGeometry().reload();
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        // Update the AR object if necessary.
        // Synchronize against concurrent access with the setter below.

        synchronized (this) {

            /*if (mLineUpdated) {
                if (mLine != null) {
                    getCurrentScene().removeChild(mLine);
                }
                if (mPoints != null) {
                    mLine = new Line3D(mPoints, 50, Color.BLUE);
                    //mLine = new Sphere(1,24,24);
                    Material m = new Material();
                    m.setColor(Color.BLUE);
                    mLine.setMaterial(m);
                    getCurrentScene().addChild(mLine);
                    Sphere ddd = new Sphere(0.1f,24,24);
                    Material m1 = new Material();
                    m1.setColor(Color.BLUE);
                    ddd.setMaterial(m1);
                 //   ddd.setPosition(mPoints.peek());
                    getCurrentScene().addChild(ddd);
                } else {
                    mLine = null;
                }
                mLineUpdated = false;
            }*/
            if (mObjectPoseUpdated) {
                Object3D mObject = new Object3D();
                float CUBE_SIDE_LENGTH = 0.1f;

                for(Vector3 cord : allAppCord){
                    Plane plane = new Plane(CUBE_SIDE_LENGTH,CUBE_SIDE_LENGTH,24,24);
                    Material planeMaterial = new Material();
                    planeMaterial.setColor(0xff009900);
                    planeMaterial.setColorInfluence(0.2f);
                    planeMaterial.enableLighting(true);
                    planeMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
                    plane.setMaterial(planeMaterial);

                    plane.setPosition(cord);

                    mObject.addChild(plane);
                    plane.moveForward(CUBE_SIDE_LENGTH / 2.0);
                    plane.moveRight(CUBE_SIDE_LENGTH / 2.0);
                    plane.setRotation(new Vector3(1.0f, 0.0f, 0.0f), 90.0f);

                }
                //mObject.setPosition(0, 0, -3);
                //cube.moveUp(CUBE_SIDE_LENGTH / 2.0);

                getCurrentScene().addChild(mObject);
                // Place the 3D object in the location of the detected plane.
                mObject.setPosition(mObjectTransform.getTranslation());
                // Note that Rajawali uses left-hand convention for Quaternions so we need to
                // specify a quaternion with rotation in the opposite direction.
                mObject.setOrientation(new Quaternion().fromMatrix(mObjectTransform));
                // Move it forward by half of the size of the cube to make it
                // flush with the plane surface.
                mObjectPoseUpdated = false;
            }

        }

        super.onRender(elapsedRealTime, deltaTime);
    }

    public synchronized void setLine(ArrayList<Vector3> points) {
        allAppCord = points;
        mLineUpdated = true;
    }
    public synchronized void updateObjectPose(float[] openglTDepthArr, float[] mDepthTPlaneArr) {
        Matrix4 openglTDepth = new Matrix4(openglTDepthArr);
        Matrix4 openglTPlane =
                openglTDepth.multiply(new Matrix4(mDepthTPlaneArr));

        mObjectTransform = openglTPlane.multiply(DEPTH_T_OPENGL);;
        mObjectPoseUpdated = true;
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The camera pose should match the pose of the camera color at the time of the last rendered
     * RGB frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread; it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        // Conjugating the Quaternion is needed because Rajawali uses left-handed convention for
        // quaternions.
        getCurrentCamera().setRotation(quaternion.conjugate());
        getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread; it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        mSceneCameraConfigured = false;
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scene camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(float[] matrixFloats) {
        getCurrentCamera().setProjectionMatrix(new Matrix4(matrixFloats));
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }
}
