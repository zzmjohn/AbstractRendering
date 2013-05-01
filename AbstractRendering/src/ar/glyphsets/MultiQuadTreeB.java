package ar.glyphsets;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import ar.GlyphSet;
import ar.Util;


/**Quad tree where items appear in each node that they touch.  
 * No items are held in intermediate nodes.
 * This version pre-computes the bounds of sub-divisions and stores them for later reference
 * as a possible optimization.  
 * **/
public abstract class MultiQuadTreeB implements GlyphSet {
	private static final double MIN_DIM = .0001d;
	
	private static final class Subs {
		public final Rectangle2D NW, NE, SW,SE;
		public final Rectangle2D[] subs = new Rectangle2D[4];
		public Subs (final Rectangle2D current) {
			double w = current.getWidth()/2;
			double h = current.getHeight()/2;
			NW = new Rectangle2D.Double(current.getX(), current.getY(),w,h);
			NE  = new Rectangle2D.Double(current.getCenterX(), current.getY(), w,h);
			SW  = new Rectangle2D.Double(current.getX(), current.getCenterY(), w,h);
			SE  = new Rectangle2D.Double(current.getCenterX(), current.getCenterY(), w,h);
			subs[0] = NW;
			subs[1] = NE;
			subs[2] = SW;
			subs[3] = SE;
		}
	}
	
	/**How many items before exploring subdivisions.**/
	private final int loading;
	protected final Rectangle2D concernBounds;
	protected final Subs subs; //What are the sub-quads; null if at bottom


	public static MultiQuadTreeB make(int loading, Rectangle2D canvasBounds) {return new MultiQuadTreeB.InnerNode(loading, canvasBounds);}
	public static MultiQuadTreeB make(int loading, int centerX, int centerY, int span) {
		return make(loading, new Rectangle2D.Double(centerX-span,centerY-span,span*2,span*2));
	}
	
	protected MultiQuadTreeB(int loading, Rectangle2D concernBounds, Subs subs) {
		this.loading=loading;
		this.concernBounds = concernBounds;
		this.subs = subs;
	}
	
	public Rectangle2D concernBounds() {return concernBounds;}
	public abstract boolean add(Glyph glyph);

	
	public int size() {return items().size();}
	public Collection<Glyph> items() {
		Collection<Glyph> collector = new HashSet<Glyph>();
		items(collector);
		return collector;		
	}
	protected abstract void items(Collection<Glyph> collector);
	
	public Collection<Glyph> containing(Point2D p) {
		Collection<Glyph> collector = new HashSet<Glyph>();
		containing(p, collector);
		return collector;
	}
	protected abstract void containing(Point2D p, Collection<Glyph> collector);
	
	public abstract String toString(int indent);

	
	private static final class LeafNode extends MultiQuadTreeB {
		private final List<Glyph> items = new ArrayList<Glyph>();
		private boolean bottom;//If you're at bottom, you don't try to split anymore.
		private int multiSubItems; //How many items touch more than one sub-quad?


		private LeafNode(int loading, Rectangle2D concernBounds) {
			super(loading,concernBounds, atBottom(concernBounds) ? null: new Subs(concernBounds));
			bottom = atBottom(concernBounds);
		}
		private static final boolean atBottom(Rectangle2D concernBounds) {return concernBounds.getWidth()<=MIN_DIM;}
		
		/**Add an item to this node.  Returns true if the item was added.  False otherwise.
		 * Will return false only if the item count exceeds the load AND the bottom has not been reached AND 
		 * the split passes the "Advantage."
		 * **/
		public boolean add(Glyph glyph) {
			if (!bottom && items.size() >= super.loading && advantageousSplit()) {
				if (multiSubItems > 0) {System.out.printf("MS %d vs. %d\n", multiSubItems, items.size());}
				return false;
			} else {
				if (!bottom) {
					multiSubItems = touchesSubs(glyph.shape.getBounds2D(), subs.subs) > 1 ? multiSubItems++ : multiSubItems;
				}
				items.add(glyph);
				return true;
			}
		}
		
		/**Check that at least half of the items will be uniquely assigned to a sub-region.**/
		public boolean advantageousSplit() {return multiSubItems > (items.size()/2);}
		private final int touchesSubs(Rectangle2D bounds, Rectangle2D[] quads) {
			int count = 0;
			for (Rectangle2D quad: quads) {if (bounds.intersects(quad)) {count++;}}
			return count;
		}

		protected void containing(Point2D p, Collection<Glyph> collector) {
			for (Glyph g: items) {if (g.shape.contains(p)) {collector.add(g);} g.shape.contains(3d,4d);}
		}
		
		protected void items(Collection<Glyph> collector) {collector.addAll(items);}
		public Rectangle2D bounds() {return Util.bounds(items);}
		public boolean isEmpty() {return items.size()==0;}
		public String toString() {return toString(0);}
		public String toString(int level) {return Util.indent(level) + "Leaf: " + items.size() + " items\n";}
	}	
	
	private static final class InnerNode extends MultiQuadTreeB {
		private MultiQuadTreeB NW,NE,SW,SE;
		
		private InnerNode(int loading, Rectangle2D concernBounds) {
			super(loading,concernBounds, new Subs(concernBounds));
			NW = new MultiQuadTreeB.LeafNode(loading, subs.NW);
			NE = new MultiQuadTreeB.LeafNode(loading, subs.NE);
			SW = new MultiQuadTreeB.LeafNode(loading, subs.SW);
			SE = new MultiQuadTreeB.LeafNode(loading, subs.SE);
		}
		
		public boolean add(Glyph glyph) {
			boolean added = false;
			Rectangle2D glyphBounds = glyph.shape.getBounds2D();
			
			if (NW.concernBounds.intersects(glyphBounds)) {
				MultiQuadTreeB q=addTo(NW, glyph);
				if (q!=NW) {NW = q;}
				added=true;
			} 
			
			if(NE.concernBounds.intersects(glyphBounds)) {
				MultiQuadTreeB q=addTo(NE, glyph);
				if (q!=NE) {NE = q;}
				added=true;
			} 
			
			if(SW.concernBounds.intersects(glyphBounds)) {
				MultiQuadTreeB q=addTo(SW, glyph);
				if (q!=SW) {SW = q;}
				added=true;
			} 
			
			if(SE.concernBounds.intersects(glyphBounds)) {
				MultiQuadTreeB q=addTo(SE, glyph);
				if (q!=SE) {SE = q;}
				added=true;
			} 
			
			if (!added) {
				throw new Error(String.format("Did not add glyph bounded %s to node with concern %s", glyphBounds, concernBounds));
			}
			else{return true;}
		}

		protected static MultiQuadTreeB addTo(MultiQuadTreeB target, Glyph item) {
			boolean added = target.add(item);
			if (added) {return target;}
			else {
				MultiQuadTreeB inner = new InnerNode(target.loading, target.concernBounds);
				for (Glyph g:target.items()) {inner.add(g);}
				inner.add(item);
				return inner;
			}
		}
		
		
		public void containing(Point2D p, Collection<Glyph> collector) {
			if (NW.concernBounds.contains(p)) {NW.containing(p,collector);}
			else if (NE.concernBounds.contains(p)) {NE.containing(p,collector);}
			else if (SW.concernBounds.contains(p)) {SW.containing(p,collector);}
			else if (SE.concernBounds.contains(p)) {SE.containing(p,collector);}
		}

		@Override
		public boolean isEmpty() {
			return  NW.isEmpty()
					&& NE.isEmpty()
					&& SW.isEmpty()
					&& SE.isEmpty();
		}

		public void items(Collection<Glyph> collector) {
			NW.items(collector);
			NE.items(collector);
			SW.items(collector);
			SE.items(collector);
		}

		public Rectangle2D bounds() {return Util.fullBounds(NW.bounds(), NE.bounds(), SW.bounds(), SE.bounds());}

		public String toString() {return toString(0);}
		public String toString(int indent) {
			return String.format("%sNode: %d items\n", Util.indent(indent), size())
						+ "NW " + NW.toString(indent+1)
						+ "NE " + NE.toString(indent+1)
						+ "SW " + SW.toString(indent+1)
						+ "SE " + SE.toString(indent+1);
		}
	}
}