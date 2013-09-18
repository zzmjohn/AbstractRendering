package ar.app.display;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ExecutorService;

import ar.*;
import ar.app.util.MostRecentOnlyExecutor;
import ar.app.util.ZoomPanHandler;

public class FullDisplay extends ARComponent.Aggregating {
	protected static final long serialVersionUID = 1L;

	protected final SimpleDisplay display;
	
	protected Aggregator<?,?> aggregator;
	protected Glyphset<?> dataset;
	protected Renderer renderer;
	
	protected AffineTransform viewTransformRef = new AffineTransform();
	protected AffineTransform inverseViewTransformRef = new AffineTransform();

	protected volatile boolean renderAgain = false;
	protected volatile boolean renderError = false;
	protected volatile Aggregates<?> aggregates;
	protected ExecutorService renderPool = new MostRecentOnlyExecutor(1,"ARPanel Render Thread");//TODO: Redoing painting to use futures...
		
	public FullDisplay(Aggregator<?,?> aggregator, Transfer<?,?> transfer, Glyphset<?> glyphs, Renderer renderer) {
		super();
		display = new SimpleDisplay(null, transfer, renderer);
		this.setLayout(new BorderLayout());
		this.add(display, BorderLayout.CENTER);
		this.invalidate();
		this.aggregator = aggregator;
		this.dataset = glyphs;
		this.renderer = renderer;
		
		ZoomPanHandler h = new ZoomPanHandler();
		super.addMouseListener(h);
		super.addMouseMotionListener(h);
	}
	
	protected void finalize() {renderPool.shutdown();}
	
	protected FullDisplay build(Aggregator<?,?> aggregator, Transfer<?,?> transfer, Glyphset<?> glyphs, Renderer renderer) {
		return new FullDisplay(aggregator, transfer, glyphs, renderer);
	}

	public Aggregates<?> refAggregates() {return display.refAggregates();}
	public void refAggregates(Aggregates<?> aggregates) {display.refAggregates(aggregates);}
	
	public Renderer renderer() {return renderer;}
	
	public Glyphset<?> dataset() {return dataset;}
	public void dataset(Glyphset<?> data) {
		this.dataset = data;
		this.aggregates = null;
	}
	
	public Transfer<?,?> transfer() {return display.transfer();}
	public void transfer(Transfer<?,?> t) {this.display.transfer(t);}
	
	public Aggregator<?,?> aggregator() {return aggregator;}
	public void aggregator(Aggregator<?,?> aggregator) {
		this.aggregator = aggregator;
		this.aggregates = null;
	}
	
	public FullDisplay withRenderer(Renderer r) {
		FullDisplay p = build(aggregator, display.transfer(), dataset, r);
		return p;
	}
	
	public Aggregates<?> aggregates() {return aggregates;}
	public Aggregator<?,?> reduction() {return aggregator;}
	public void aggregates(Aggregates<?> aggregates) {
		this.display.aggregates(aggregates);
		this.aggregates = aggregates;
	}
	
	
	@Override
	public void paint(Graphics g) {
		panelPaint(g);
		super.paint(g);
	}
	
	//Override this method in subclasses to make custom painting
	protected void panelPaint(Graphics g) {
		Runnable action = null;
		if (renderer == null 
				|| dataset == null ||  dataset.isEmpty() 
				|| aggregator == null
				|| renderError == true) {
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
		} else if (renderAgain || aggregates == null) {
			action = new RenderAggregates();
		} 

		if (action != null) {
			renderPool.execute(action);
			renderAgain =false; 
		} 
	}
	
	/**Calculate aggregates for a given region.**/
	protected final class RenderAggregates implements Runnable {
		@SuppressWarnings({"unchecked","rawtypes"})
		public void run() {
			int width = FullDisplay.this.getWidth();
			int height = FullDisplay.this.getHeight();
			long start = System.currentTimeMillis();
			AffineTransform ivt = inverseViewTransform();
			try {
				aggregates = renderer.aggregate(dataset, (Aggregator) aggregator, ivt, width, height);
				display.aggregates(aggregates);
				long end = System.currentTimeMillis();
				if (PERF_REP) {
					System.out.printf("%d ms (Aggregates render on %d x %d grid)\n",
							(end-start), aggregates.highX()-aggregates.lowX(), aggregates.highY()-aggregates.lowY());
				}
			} catch (ClassCastException e) {
				renderError = true;
			}
			
			FullDisplay.this.repaint();
		}
	}
	
	
	public String toString() {return String.format("ARPanel[Dataset: %1$s, Ruleset: %2$s]", dataset, display.transfer(), aggregator);}
	
	
	
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
        try {innerSetViewTransform(vt);}
        catch (NoninvertibleTransformException e ) {
        	try {innerSetViewTransform(new AffineTransform());}
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
        try {innerSetViewTransform(vt);}
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
        try {innerSetViewTransform(vt);}
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
	protected void innerSetViewTransform(AffineTransform vt) throws NoninvertibleTransformException {
		renderAgain = true;
		viewTransform(vt);
	}
	
	public void viewTransform(AffineTransform vt) throws NoninvertibleTransformException {		
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
			
			//TODO:  start using util zoomFit;  need to fix the tight-bounds problem  
//			AffineTransform vt = Util.zoomFit(content, getWidth(), getHeight());
//			setViewTransform(vt);
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