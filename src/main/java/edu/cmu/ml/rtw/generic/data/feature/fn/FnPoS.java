package edu.cmu.ml.rtw.generic.data.feature.fn;

import java.util.Collection;

import edu.cmu.ml.rtw.generic.cluster.ClustererTokenSpanPoSTag;
import edu.cmu.ml.rtw.generic.data.Context;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.TokenSpan;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.Obj;

/**
 * FnPos takes a collection of token spans and computes
 * their corresponding part-of-speech tag
 * sequences.
 * 
 * @author Bill McDowell
 *
 */
public class FnPoS extends Fn<TokenSpan, String> {
	private ClustererTokenSpanPoSTag clusterer = new ClustererTokenSpanPoSTag();
	private Context context;
	
	private boolean literalSymbols = false;
	private String[] parameterNames = { "literalSymbols" };
	
	public FnPoS() {
		
	}
	
	public FnPoS(Context context) {
		this.context = context;
	}

	@Override
	public String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("literalSymbols"))
			return Obj.stringValue(String.valueOf(this.literalSymbols));
		else
			return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("literalSymbols"))
			this.literalSymbols = Boolean.valueOf(this.context.getMatchValue(parameterValue));
		else
			return false;
		return true;
	}

	@Override
	public <C extends Collection<String>> C compute(Collection<TokenSpan> input, C output) {
		for (TokenSpan tokenSpan : input) {
			output.addAll(this.clusterer.getClusters(tokenSpan, this.literalSymbols));
		}
		
		return output;
	}

	@Override
	public Fn<TokenSpan, String> makeInstance(Context context) {
		return new FnPoS(context);
	}

	@Override
	protected boolean fromParseInternal(AssignmentList internalAssignments) {
		return true;
	}

	@Override
	protected AssignmentList toParseInternal() {
		return null;
	}

	@Override
	public String getGenericName() {
		return "PoS";
	}

}
