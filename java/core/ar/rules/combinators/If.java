package ar.rules.combinators;

import ar.Aggregates;
import ar.Renderer;
import ar.Transfer;
import ar.glyphsets.implicitgeometry.Valuer;
import ar.rules.General;

/**If/Then/Else implementation.
 * 
 * If both the pass and fail transfer functions are {@link ar.Transfer.ItemWise},
 * then the predicate will be run item-wise.  Otherwise, the predicate will
 * be applied to the whole set (via {@link ar.rules.combiners.Predicates.All}).
 * 
 *  TODO: Convert predicate to be a transfer function too.  For bulk operation, use an 'All' aggregator and rollup down to a single value.
 **/
public class If<IN,OUT> implements Transfer<IN,OUT> {
    protected final Valuer<IN, Boolean> pred;
    protected final Transfer<IN,OUT> pass;
    protected final Transfer<IN,OUT> fail;
    protected final OUT empty;

    /**The empty value will be taken from fail.**/
    public If(Valuer<IN, Boolean> pred, Transfer<IN,OUT> pass, Transfer<IN,OUT> fail) {
        this(pred, pass, fail, fail.emptyValue());
    }
    
    /**The empty value will be the same as the fail value.**/
    public If(Valuer<IN, Boolean> pred, OUT pass, OUT fail) {
    	this(pred, new General.Const<IN,OUT>(pass), new General.Const<IN,OUT>(fail), fail);
    }

    public If(Valuer<IN, Boolean> pred, Transfer<IN,OUT> pass, Transfer<IN,OUT> fail, OUT empty) {
        this.pred = pred;
        this.pass = pass;
        this.fail = fail;
        this.empty = empty;
    }
    @Override public OUT emptyValue() {return empty;}

    @Override
    public Specialized<IN, OUT> specialize(Aggregates<? extends IN> aggregates) {
        Specialized<IN,OUT> ps = pass.specialize(aggregates);
        Specialized<IN,OUT> fs = fail.specialize(aggregates);
        
        if (ps instanceof Transfer.ItemWise && fs instanceof Transfer.ItemWise) {
        	return new ItemWise<>(pred, (Transfer.ItemWise<IN,OUT>) ps, (Transfer.ItemWise<IN,OUT>) fs, empty);
        } else {
        	return new SetWise<IN,OUT>(pred, ps, fs, empty);
        }
    }


    /**Check if all values in an aggregate set pass a predicate,
     * dispatches to pass/fail transfer accordingly.
     * 
     * TODO: Add support for 'ignoreDefault' parameter on the 'all' predicate.
     */
    public static class SetWise<IN,OUT> extends If<IN,OUT> implements Transfer.Specialized<IN,OUT> {
        final Specialized<IN,OUT> pass;
        final Specialized<IN,OUT> fail;

		public SetWise(Valuer<IN, Boolean> pred, 
				Specialized<IN, OUT> pass,
				Specialized<IN, OUT> fail, 
				OUT empty) {
			super(pred, pass, fail, empty);
			this.pass = pass;
			this.fail = fail;
		}

		@Override
		public Aggregates<OUT> process(Aggregates<? extends IN> aggregates, Renderer rend) {
			if (Predicates.All.value(aggregates, pred, false)) {return pass.process(aggregates, rend);}
			else {return fail.process(aggregates, rend);}
		}
    }

    
    /**Check if a value at an x/y location passes a predicate,
     * dispatches to the pass or fail transfer accordingly.
     */
    public static class ItemWise<IN,OUT> extends If<IN,OUT> implements Transfer.ItemWise<IN,OUT> {
        final Transfer.ItemWise<IN,OUT> pass;
        final Transfer.ItemWise<IN,OUT> fail;

        public ItemWise(
        		Valuer<IN, Boolean> pred,
                Transfer.ItemWise<IN,OUT> pass,
                Transfer.ItemWise<IN,OUT> fail,
                OUT empty) {

            super(pred,pass,fail,empty);
            this.pass = pass;
            this.fail = fail;
        }

        @Override
        public OUT at(int x, int y, Aggregates<? extends IN> aggregates) {
            IN val = aggregates.get(x,y);
            if (pred.value(val)) {return pass.at(x,y,aggregates);}
            else {return fail.at(x,y,aggregates);}
        }

		@Override
		public Aggregates<OUT> process(Aggregates<? extends IN> aggregates, Renderer rend) {
			return rend.transfer(aggregates, this);
		}
    }
}
