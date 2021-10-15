package btools.routingapp;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import btools.router.RoutingHelper;

public class BInstallerView extends View {
  public static final int MASK_SELECTED_RD5 = 1;
  public static final int MASK_DELETED_RD5 = 2;
  public static final int MASK_INSTALLED_RD5 = 4;
  public static final int MASK_CURRENT_RD5 = 8;
  private final File baseDir;
  private final File segmentDir;
  private final Matrix mat;
  private final Bitmap bmp;
  private final float viewscale;
  private final int[] tileStatus;
  private final int imgwOrig;
  private final int imghOrig;
  private final float scaleOrig;
  private final int imgw;
  private final int imgh;
  private final float[] testVector = new float[2];
  private final Matrix matText;
  Paint pnt_1 = new Paint();
  Paint pnt_2 = new Paint();
  Paint paint = new Paint();
  Activity mActivity;
  int btnh = 40;
  int btnw = 160;
  float tx, ty;
  private float lastDownX;
  private float lastDownY;
  private boolean tilesVisible = false;
  private long availableSize;
  private long totalSize = 0;
  private long rd5Tiles = 0;
  private long delTiles = 0;
  private OnClickListener mOnClickListener;

  public BInstallerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mActivity = (Activity) context;

    DisplayMetrics metrics = new DisplayMetrics();
    ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(metrics);
    imgwOrig = metrics.widthPixels;
    imghOrig = metrics.heightPixels;
    int im = Math.max(imgwOrig, imghOrig);

    scaleOrig = im / 480.f;

    matText = new Matrix();
    matText.preScale(scaleOrig, scaleOrig);

    imgw = (int) (imgwOrig / scaleOrig);
    imgh = (int) (imghOrig / scaleOrig);

    baseDir = ConfigHelper.getBaseDir(getContext());
    segmentDir = new File(baseDir, "brouter/segments4");

    try {
      AssetManager assetManager = getContext().getAssets();
      InputStream istr = assetManager.open("world.png");
      bmp = BitmapFactory.decodeStream(istr);
      istr.close();
    } catch (IOException io) {
      throw new RuntimeException("cannot read world.png from assets");
    }

    tileStatus = new int[72 * 36];
    scanExistingFiles();

    float scaleX = imgwOrig / ((float) bmp.getWidth());
    float scaley = imghOrig / ((float) bmp.getHeight());

    viewscale = Math.min(scaleX, scaley);

    mat = new Matrix();
    mat.postScale(viewscale, viewscale);
  }

  private void scanExistingFiles() {

  }

  public void setAvailableSize(long availableSize) {
    this.availableSize = availableSize;
  }

  public void setTileStatus(int tileIndex, int tileMask) {
    tileStatus[tileIndex] |= tileMask;
  }

  public void clearAllTilesStatus(int tileMask) {
    for (int ix = 0; ix < 72; ix++) {
      for (int iy = 0; iy < 36; iy++) {
        int tileIndex = gridPos2Tileindex(ix, iy);
        tileStatus[tileIndex] ^= tileStatus[tileIndex] & tileMask;
      }
    }
    invalidate();
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    mOnClickListener = listener;
  }

  protected String baseNameForTile(int tileIndex) {
    int lon = (tileIndex % 72) * 5 - 180;
    int lat = (tileIndex / 72) * 5 - 90;
    String slon = lon < 0 ? "W" + (-lon) : "E" + lon;
    String slat = lat < 0 ? "S" + (-lat) : "N" + lat;
    return slon + "_" + slat;
  }

  private int gridPos2Tileindex(int ix, int iy) {
    return (35 - iy) * 72 + (ix >= 70 ? ix - 70 : ix + 2);
  }

  public void toggleDownload() {
    if (delTiles > 0) {
      ((BInstallerActivity) getContext()).showConfirmDelete();
      return;
    }

    int min_size = Integer.MAX_VALUE;

    ArrayList<Integer> downloadList = new ArrayList<>();
    // prepare download list
    for (int ix = 0; ix < 72; ix++) {
      for (int iy = 0; iy < 36; iy++) {
        int tidx = gridPos2Tileindex(ix, iy);
        if ((tileStatus[tidx] & MASK_SELECTED_RD5) != 0) {
          int tilesize = BInstallerSizes.getRd5Size(tidx);
          downloadList.add(tidx);
          if (tilesize > 0 && tilesize < min_size) {
            min_size = tilesize;
          }
        }
      }
    }

    if (downloadList.size() > 0) {
      // isDownloading = true;
      ((BInstallerActivity) getContext()).downloadAll(downloadList);
      for (Integer i : downloadList) {
        tileStatus[i] ^= tileStatus[i] & MASK_SELECTED_RD5;
      }
      downloadList.clear();
    }
  }

  private int tileIndex(float x, float y) {
    int ix = (int) (72.f * x / bmp.getWidth());
    int iy = (int) (36.f * y / bmp.getHeight());
    if (ix >= 0 && ix < 72 && iy >= 0 && iy < 36) return gridPos2Tileindex(ix, iy);
    return -1;
  }

  // get back the current image scale
  private float currentScale() {
    testVector[1] = 1.f;
    mat.mapVectors(testVector);
    return testVector[1] / viewscale;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.setMatrix(mat);
    canvas.drawBitmap(bmp, 0, 0, null);
    // draw 5*5 lattice starting at scale=3

    int iw = bmp.getWidth();
    int ih = bmp.getHeight();
    float fw = iw / 72.f;
    float fh = ih / 36.f;

    if (tilesVisible) {
      pnt_1.setColor(Color.GREEN);
      pnt_1.setStyle(Paint.Style.STROKE);
      for (int ix = 0; ix < 72; ix++) {
        for (int iy = 0; iy < 36; iy++) {
          int tidx = gridPos2Tileindex(ix, iy);
          int tilesize = BInstallerSizes.getRd5Size(tidx);
          if (tilesize > 0) {
            canvas.drawRect(fw * ix, fh * (iy + 1), fw * (ix + 1), fh * iy, pnt_1);
          }
        }
      }
    }

    rd5Tiles = 0;
    delTiles = 0;
    totalSize = 0;
    int mask2 = MASK_SELECTED_RD5 | MASK_DELETED_RD5 | MASK_INSTALLED_RD5;
    int mask3 = mask2 | MASK_CURRENT_RD5;

    pnt_2.setStyle(Paint.Style.STROKE);
    pnt_2.setColor(Color.GRAY);
    pnt_2.setStrokeWidth(1);
    drawSelectedTiles(canvas, pnt_2, fw, fh, MASK_INSTALLED_RD5, mask3, false, false, tilesVisible);
    pnt_2.setColor(Color.BLUE);
    pnt_2.setStrokeWidth(1);
    drawSelectedTiles(canvas, pnt_2, fw, fh, MASK_INSTALLED_RD5 | MASK_CURRENT_RD5, mask3, false, false, tilesVisible);
    pnt_2.setColor(Color.GREEN);
    pnt_2.setStrokeWidth(2);
    drawSelectedTiles(canvas, pnt_2, fw, fh, MASK_SELECTED_RD5, mask2, true, false, tilesVisible);
    pnt_2.setColor(Color.YELLOW);
    pnt_2.setStrokeWidth(2);
    drawSelectedTiles(canvas, pnt_2, fw, fh, MASK_SELECTED_RD5 | MASK_INSTALLED_RD5, mask2, true, false, tilesVisible);
    pnt_2.setColor(Color.RED);
    pnt_2.setStrokeWidth(2);
    drawSelectedTiles(canvas, pnt_2, fw, fh, MASK_DELETED_RD5 | MASK_INSTALLED_RD5, mask2, false, true, tilesVisible);

    canvas.setMatrix(matText);

    paint.setColor(Color.RED);

    long mb = 1024 * 1024;

    if (!this.tilesVisible) {
      paint.setTextSize(35);
      canvas.drawText("Touch region to zoom in!", 30, (imgh / 3) * 2, paint);
    }
    paint.setTextSize(20);


    String totmb = ((totalSize + mb - 1) / mb) + " MB";
    String freemb = availableSize >= 0 ? ((availableSize + mb - 1) / mb) + " MB" : "?";
    canvas.drawText("Selected segments=" + rd5Tiles, 10, 25, paint);
    canvas.drawText("Size=" + totmb + " Free=" + freemb, 10, 45, paint);


    String btnText = null;
    if (delTiles > 0) btnText = "Delete " + delTiles + " tiles";
    else if (rd5Tiles > 0) btnText = "Start Download";
    else if (this.tilesVisible &&
      rd5Tiles == 0 &&
      RoutingHelper.hasDirectoryAnyDatafiles(segmentDir)) btnText = "Update all";

    if (btnText != null) {
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawRect(imgw - btnw, imgh - btnh, imgw - 2, imgh - 2, paint);
      paint.setStyle(Paint.Style.FILL);
      canvas.drawText(btnText, imgw - btnw + 5, imgh - 10, paint);
    }
  }

  private void drawSelectedTiles(Canvas canvas, Paint pnt, float fw, float fh, int status, int mask, boolean doCount, boolean cntDel, boolean doDraw) {
    for (int ix = 0; ix < 72; ix++)
      for (int iy = 0; iy < 36; iy++) {
        int tidx = gridPos2Tileindex(ix, iy);
        if ((tileStatus[tidx] & mask) == status) {
          int tilesize = BInstallerSizes.getRd5Size(tidx);
          if (tilesize > 0) {
            if (doCount) {
              rd5Tiles++;
              totalSize += BInstallerSizes.getRd5Size(tidx);
            }
            if (cntDel) {
              delTiles++;
              totalSize += BInstallerSizes.getRd5Size(tidx);
            }
            if (!doDraw)
              continue;
            // draw cross
            canvas.drawLine(fw * ix, fh * iy, fw * (ix + 1), fh * (iy + 1), pnt);
            canvas.drawLine(fw * ix, fh * (iy + 1), fw * (ix + 1), fh * iy, pnt);

            // draw frame
            canvas.drawRect(fw * ix, fh * (iy + 1), fw * (ix + 1), fh * iy, pnt);
          }
        }
      }
  }

  public void deleteSelectedTiles() {
    for (int ix = 0; ix < 72; ix++) {
      for (int iy = 0; iy < 36; iy++) {
        int tidx = gridPos2Tileindex(ix, iy);
        if ((tileStatus[tidx] & MASK_DELETED_RD5) != 0) {
          new File(baseDir, "brouter/segments4/" + baseNameForTile(tidx) + ".rd5").delete();
        }
      }
    }
    scanExistingFiles();
    invalidate();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {

    // get masked (not specific to a pointer) action
    int maskedAction = event.getActionMasked();

    switch (maskedAction) {

      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN: {
        lastDownX = event.getX();
        lastDownY = event.getY();

        break;
      }
      case MotionEvent.ACTION_MOVE: { // a pointer was moved

        int np = event.getPointerCount();
        int nh = event.getHistorySize();
        if (nh == 0) break;

        float x0 = event.getX(0);
        float y0 = event.getY(0);
        float hx0 = event.getHistoricalX(0, 0);
        float hy0 = event.getHistoricalY(0, 0);

        if (np > 1) // multi-touch
        {
          float x1 = event.getX(1);
          float y1 = event.getY(1);
          float hx1 = event.getHistoricalX(1, 0);
          float hy1 = event.getHistoricalY(1, 0);

          float r = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
          float hr = (float) Math.sqrt((hx1 - hx0) * (hx1 - hx0) + (hy1 - hy0) * (hy1 - hy0));

          if (hr > 0.) {
            float ratio = r / hr;

            float mx = (x1 + x0) / 2.f;
            float my = (y1 + y0) / 2.f;

            float scale = currentScale();
            float newscale = scale * ratio;

            if (newscale > 10.f) ratio *= (10.f / newscale);
            if (newscale < 0.5f) ratio *= (0.5f / newscale);

            mat.postScale(ratio, ratio, mx, my);

            mat.postScale(ratio, ratio, mx, my);

            boolean tilesv = currentScale() >= 3.f;
            if (tilesVisible && !tilesv) {
              clearAllTilesStatus(MASK_SELECTED_RD5 | MASK_DELETED_RD5);
            }
            tilesVisible = tilesv;
          }

          break;
        }
        mat.postTranslate(x0 - hx0, y0 - hy0);

        break;
      }
      case MotionEvent.ACTION_UP:

        long downTime = event.getEventTime() - event.getDownTime();

        if (downTime < 5 || downTime > 500) {
          break;
        }

        if (Math.abs(lastDownX - event.getX()) > 10 || Math.abs(lastDownY - event.getY()) > 10) {
          break;
        }

        // download button?
        if ((delTiles > 0 || rd5Tiles >= 0) && event.getX() > imgwOrig - btnw * scaleOrig && event.getY() > imghOrig - btnh * scaleOrig) {
          if (rd5Tiles == 0) {
            for (int ix = 0; ix < 72; ix++) {
              for (int iy = 0; iy < 36; iy++) {
                int tidx = gridPos2Tileindex(ix, iy);
                if (tidx != -1) {
                  if ((tileStatus[tidx] & MASK_INSTALLED_RD5) != 0) {
                    tileStatus[tidx] |= MASK_SELECTED_RD5;
                  }
                }

              }
            }
          }
          if (mOnClickListener != null) {
            mOnClickListener.onClick(null);
          }
          invalidate();
          break;
        }

        if (!tilesVisible) {
          float scale = currentScale();
          if (scale > 0f && scale < 5f) {
            float ratio = 5f / scale;
            mat.postScale(ratio, ratio, event.getX(), event.getY());
            tilesVisible = true;
          }
          break;
        }

        Matrix imat = new Matrix();
        if (mat.invert(imat)) {
          float[] touchpoint = new float[2];
          touchpoint[0] = event.getX();
          touchpoint[1] = event.getY();
          imat.mapPoints(touchpoint);

          int tidx = tileIndex(touchpoint[0], touchpoint[1]);
          if (tidx != -1) {
            if ((tileStatus[tidx] & MASK_SELECTED_RD5) != 0) {
              tileStatus[tidx] ^= MASK_SELECTED_RD5;
              if ((tileStatus[tidx] & MASK_INSTALLED_RD5) != 0) {
                tileStatus[tidx] |= MASK_DELETED_RD5;
              }
            } else if ((tileStatus[tidx] & MASK_DELETED_RD5) != 0) {
              tileStatus[tidx] ^= MASK_DELETED_RD5;
            } else {
              tileStatus[tidx] ^= MASK_SELECTED_RD5;
            }
          }

          tx = touchpoint[0];
          ty = touchpoint[1];
        }


        break;
      case MotionEvent.ACTION_POINTER_UP:
      case MotionEvent.ACTION_CANCEL: {
        // TODO use data
        break;
      }
    }
    invalidate();

    return true;
  }


}
