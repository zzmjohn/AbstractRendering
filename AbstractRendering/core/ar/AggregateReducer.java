package ar;

import java.util.List;

/**Combine two aggregate values into a third composite aggregate.
 * 
 * This class is the inner function for combining two aggregate sets together.
 * (Combining aggregate sets where LEFT,RIGHT and OUT are all the same is implemented
 * in {@link ar.util.Util} "reduceAggregates".)
 * 
 * @author jcottam
 *
 * @param <LEFT>  Left-hand aggregate type
 * @param <RIGHT> Right-hand aggregate type
 * @param <OUT> Resulting aggregate type
 */
public interface AggregateReducer<LEFT,RIGHT,OUT> {

	/**Combine two aggregate values.
	 * This is a combination point-wise of the aggregate values, not of the aggregate sets.
	 * 
	 * (NOTE: If you come up with a useful case for combining aggregate sets instead of just aggregate values, please let me know.)
	 * 
	 * @param left Left-hand aggregate value
	 * @param right Right-hand aggregate value
	 * @return Combination of left and right aggregate values as a new aggregate value
	 */
	public OUT combine(LEFT left, RIGHT right);
	

	/**Reduce a few aggregate values into a single value.  
	 * This is used for reducing the resolution of a single set of aggregates (e.g., for zooming without recomputing aggregates). 
	 * 
	 * @param sources Values from the base aggregates
	 * @return Combination of the passed aggregates
	 */
	public OUT rollup(List<LEFT> sources);
	
	/** Value that is an identity under this operation in the output space.
	 * This value is used when no data is present for reduction in either aggregate or for padding in rollup.**/
	public OUT zero();
	
	public Class<LEFT> left();
	public Class<RIGHT> right();
	public Class<OUT> output();
}
