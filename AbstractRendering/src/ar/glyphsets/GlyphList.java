package ar.glyphsets;

import java.awt.geom.Rectangle2D;
import java.util.*;

import ar.GlyphSet;
import ar.util.Util;

public class GlyphList implements GlyphSet, GlyphSet.RandomAccess {
	List<Glyph> glyphs = new ArrayList<Glyph>();
	Rectangle2D bounds;
	
	public Iterator<Glyph> iterator() {return glyphs.iterator();}
	public Glyph get(int i) {return glyphs.get(i);}
	public boolean isEmpty() {return glyphs.isEmpty();}
	public void add(Glyph g) {glyphs.add(g);}
	public int size() {return glyphs.size();}

	public Collection<Glyph> intersects(Rectangle2D r) {
		ArrayList<Glyph> contained = new ArrayList<Glyph>();
		for (Glyph g: glyphs) {if (g.shape.intersects(r)) {contained.add(g);}}
		return contained;
	}
	
	public Rectangle2D bounds() {
		if (bounds == null) {bounds = Util.bounds(glyphs);}
		return bounds;		
	}
	



}
