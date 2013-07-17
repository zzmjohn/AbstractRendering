package ar.app.components;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import ar.*;
import ar.app.util.ZoomPanHandler;
import ar.util.Util;

public class ARPanel extends JPanel {
	public static boolean PERF_MON = false;
	
	private static final long serialVersionUID = 1L;
	private final Aggregator reduction;
	private final Transfer transfer;
	private final Glyphset<?> dataset;
	private Renderer renderer;
	
	private AffineTransform viewTransformRef = new AffineTransform();
	private AffineTransform inverseViewTransformRef = new AffineTransform();

	private volatile boolean renderAgain = false;
	private volatile BufferedImage image;
	private volatile Aggregates<?> aggregates;
	private Thread renderThread;
	
	public ARPanel(Aggregator<?,?> reduction, Transfer<?,?> transfer, Glyphset<?> glyphs, Renderer renderer) {
		super();
		this.reduction = reduction;
		this.transfer = transfer;
		this.dataset = glyphs;
		this.renderer = renderer;
		
		ZoomPanHandler h = new ZoomPanHandler();
		super.addMouseListener(h);
		super.addMouseMotionListener(h);
	}
	
	@SuppressWarnings("deprecation")
	protected void finalize() {
		if (renderThread != null) {renderThread.stop();}
	}
	

	public ARPanel withDataset(Glyphset<?> data) {
		return new ARPanel(reduction, transfer, data, renderer);
	}
	
	public  ARPanel withTransfer(Transfer<?,?> t) {
		ARPanel p = new ARPanel(reduction, t, dataset, renderer);
		p.viewTransformRef = this.viewTransformRef;
		p.inverseViewTransformRef = this.inverseViewTransformRef;
		p.aggregates = this.aggregates;
		return p;
	}
	
	public ARPanel withReduction(Aggregator<?,?> r) {
		ARPanel p = new ARPanel(r, transfer, dataset, renderer);
		p.viewTransformRef = this.viewTransformRef;
		p.inverseViewTransformRef = this.inverseViewTransformRef;
		return p;
	}
	
	public ARPanel withRenderer(Renderer r) {
		ARPanel p = new ARPanel(reduction, transfer, dataset, r);
		return p;
	}
	
	public Aggregates<?> aggregates() {return aggregates;}
	public Aggregator<?,?> reduction() {return reduction;}
	public void aggregates(Aggregates<?> aggregates) {this.aggregates = aggregates;}
	
	private final boolean differentSizes(BufferedImage image, JPanel p) {
		if (image == null) {return false;}
		else {return image.getWidth() != p.getWidth() || image.getHeight() != p.getHeight();}
	}
	
	@Override
	public void paintComponent(Graphics g) {
		Runnable action = null;
		if (renderer == null 
				|| dataset == null ||  dataset.isEmpty() 
				|| transfer == null || reduction == null
				|| !transfer.input().isAssignableFrom(reduction.output())) {
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
		} else if (renderAgain || aggregates == null || differentSizes(image, ARPanel.this)) {
			action = new FullRender();
		} else if (image == null) {
			action = new TransferRender();
		}

		if (action != null && (renderThread == null || !renderThread.isAlive())) {
			renderAgain =false; 
			renderThread = new Thread(action, "Render Thread");
			renderThread.setDaemon(true);
			renderThread.start();
		}
	
		if (image != null) {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
			Graphics2D g2 = (Graphics2D) g;
			g2.drawRenderedImage(image,g2.getTransform());
			if (PERF_MON) {synchronized(this) {this.notifyAll();}}
		} else {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
		}
	}
	
	public final class FullRender implements Runnable {
		public void run() {
			int width = ARPanel.this.getWidth();
			int height = ARPanel.this.getHeight();
			long start = System.currentTimeMillis();
			AffineTransform ivt = inverseViewTransform();
			aggregates = renderer.reduce(dataset, reduction, ivt, width, height);
			Aggregates<Color> colors = renderer.transfer(aggregates, transfer);
			image = Util.asImage(colors, width, height, Util.CLEAR);
			long end = System.currentTimeMillis();
			if (!PERF_MON) {System.out.printf("%,d ms (full on %d, %d grid)\n", (end-start), image.getWidth(), image.getHeight());}
			ARPanel.this.repaint();
		}
	}
	
	public final class TransferRender implements Runnable {
		public void run() {
			long start = System.currentTimeMillis();
			int width = ARPanel.this.getWidth();
			int height = ARPanel.this.getHeight();

			@SuppressWarnings("unchecked")
			Aggregates<Color> colors = renderer.transfer(aggregates, transfer);
			image = Util.asImage(colors, width, height, Util.CLEAR);
			long end = System.currentTimeMillis();
			if (!PERF_MON) {System.out.printf("%,d ms (transfer on %d, %d grid)\n", (end-start), image.getWidth(), image.getHeight());}
			ARPanel.this.repaint();
		}
	}
	
	public String toString() {return String.format("ARPanel[Dataset: %1$s, Ruleset: %2$s]", dataset, transfer, reduction);}
	public Renderer getRenderer() {return renderer;}
	public Glyphset<?> dataset() {return dataset;}
	
	
	
	Point2D tempPoint = new Point2D.Double();
	/**Zooms anchored on the given screen point TO the given scale.*/
	public void zoomTo(final Point2D p, double scale) {
		inverseViewTransform().transform(p, tempPoint);
		zoomToAbs(tempPoint, scale);
	}

	/**Zooms anchored on the given screen point TO the given scale.*/
	public void zoomToAbs(final Point2D p, double scale) {
		zoomToAbs(p, scale, scale);
	}
	
	/**Zooms anchored on the given screen point TO the given scale.*/
	public void zoomToAbs(final Point2D p, double scaleX, double scaleY) {
		zoomAbs(p, scaleX/viewTransform().getScaleX(), scaleY/viewTransform().getScaleY());
	}



	
	/**Zoom anchored on the given screen point by the given scale.*/
	public void zoom(final Point2D p, double scale) {
		inverseViewTransform().transform(p, tempPoint);
		zoomAbs(tempPoint, scale);
	}
	
	/**Zoom anchored on the given absolute point (e.g. canvas 
	 * under the identity transform) to the given scale.
	 */
	public void zoomAbs(final Point2D p, double scale) {
		zoomAbs(p, scale, scale);
	}
	
	public void zoomAbs(final Point2D p, double scaleX, double scaleY) {
		double zx = p.getX(), zy = p.getY();
		AffineTransform vt = viewTransform();
        vt.translate(zx, zy);
        vt.scale(scaleX,scaleY);
        vt.translate(-zx, -zy);
        try {setViewTransform(vt);}
        catch (NoninvertibleTransformException e ) {
        	try {setViewTransform(new AffineTransform());}
			catch (NoninvertibleTransformException e1) {}	//Default transform is invertible...so everything is safe
        }
	}
	
    /**
     * Pans the view provided by this display in screen coordinates.
     * @param dx the amount to pan along the x-dimension, in pixel units
     * @param dy the amount to pan along the y-dimension, in pixel units
     */
    public void pan(double dx, double dy) {
    	tempPoint.setLocation(dx, dy);
    	inverseViewTransform().transform(tempPoint, tempPoint);
        double panx = tempPoint.getX();
        double pany = tempPoint.getY();
        tempPoint.setLocation(0, 0);
        inverseViewTransform().transform(tempPoint, tempPoint);
        panx -= tempPoint.getX();
        pany -= tempPoint.getY();
        panAbs(panx, pany);
    }
    
    /**
     * Pans the view provided by this display in absolute (i.e. item-space)
     * coordinates.
     * @param dx the amount to pan along the x-dimension, in absolute co-ords
     * @param dy the amount to pan along the y-dimension, in absolute co-ords
     */
    public void panAbs(double dx, double dy) {
    	AffineTransform vt = viewTransform();
    	vt.translate(dx, dy);
        try {setViewTransform(vt);}
        catch (NoninvertibleTransformException e ) {throw new Error("Supposedly impossible error occured.", e);}
    }
	
	/**Pan so the display is centered on the given screen point.*/
	public void panTo(final Point2D p) {
        inverseViewTransform().transform(p, tempPoint);
        panToAbs(tempPoint);
	}
	
	/**Pan so the display is centered on the given canvas
	 * point.
	 */
	public void panToAbs(final Point2D p) {
        double sx = viewTransform().getScaleX();
        double sy = viewTransform().getScaleY();
        double x = p.getX(); x = (Double.isNaN(x) ? 0 : x);
        double y = p.getY(); y = (Double.isNaN(y) ? 0 : y);
        x = getWidth() /(2*sx) - x;
        y = getHeight()/(2*sy) - y;
        
        double dx = x-(viewTransform().getTranslateX()/sx);
        double dy = y-(viewTransform().getTranslateY()/sy);

        AffineTransform vt = viewTransform();
        vt.translate(dx, dy);
        try {setViewTransform(vt);}
        catch (NoninvertibleTransformException e ) {throw new Error("Supposedly impossible error occured.", e);}
	}

	
    /**Get the current scale factor factor (in cases
     * where it is significant, this is the X-scale).
     */
    public double getScale() {return viewTransform().getScaleX();}
    
	/**What is the current center of the screen (in canvas coordinates).
	 * 
	 *  @param target Store in this point2D.  If null a new point2D will be created.
	 **/
	public Point2D getPanAbs(Point2D target) {
		if (target == null) {target = new Point2D.Double();}
		
		Rectangle2D viewBounds = inverseViewTransform().createTransformedShape(getBounds()).getBounds2D();

		target.setLocation(viewBounds.getCenterX(), viewBounds.getCenterY());
		return target; 
	}

	 
    /**Use this transform to convert values from the absolute system
     * to the screen system.
     */
	public AffineTransform viewTransform() {return new AffineTransform(viewTransformRef);}
	public void setViewTransform(AffineTransform vt) throws NoninvertibleTransformException {
		renderAgain = true;
		transferViewTransform(vt);
	}
	
	public void transferViewTransform(AffineTransform vt) throws NoninvertibleTransformException {		
		this.viewTransformRef = vt;
		inverseViewTransformRef  = new AffineTransform(vt);
		inverseViewTransformRef.invert();
		this.repaint();
	}
	
	/**Use this transform to convert screen values to the absolute/canvas
	 * values.
	 */
	public AffineTransform inverseViewTransform() {return new AffineTransform(inverseViewTransformRef);}

	
	public void zoomFit() {
		try {
			if (dataset() == null || dataset().bounds() ==null) {return;}
			Rectangle2D content = dataset().bounds();

			double w = getWidth()/content.getWidth();
			double h = getHeight()/content.getHeight();
			double scale = Math.min(w, h);
			scale = scale/getScale();
			Point2D center = new Point2D.Double(content.getCenterX(), content.getCenterY());  

			zoomAbs(center, scale);
			panToAbs(center);
		} catch (Exception e) {} //Ignore all zoom-fit errors...they are usually caused by under-specified state
	}
}
