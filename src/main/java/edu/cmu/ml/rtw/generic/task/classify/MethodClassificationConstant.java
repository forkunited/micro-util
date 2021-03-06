package edu.cmu.ml.rtw.generic.task.classify;

import java.util.HashMap;
import java.util.Map;

import edu.cmu.ml.rtw.generic.data.annotation.DataSet;
import edu.cmu.ml.rtw.generic.data.annotation.Datum;
import edu.cmu.ml.rtw.generic.data.annotation.DatumContext;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.Obj;
import edu.cmu.ml.rtw.generic.util.Pair;

public class MethodClassificationConstant<D extends Datum<L>, L> extends MethodClassification<D, L> {
	private L label;
	private double score = 1.0;
	private String[] parameterNames = { "label", "score" };
	
	public MethodClassificationConstant() {
		this(null);
	}
	
	public MethodClassificationConstant(DatumContext<D, L> context) {
		super(context);
	}

	@Override
	public String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("label"))
			return Obj.stringValue(this.label.toString());
		else if (parameter.equals("score"))
			return Obj.stringValue(String.valueOf(this.score));
		else 
			return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("label"))
			this.label = (parameterValue == null) ? null : this.context.getDatumTools().labelFromString(this.context.getMatchValue(parameterValue));
		else if (parameter.equals("score"))
			this.score = Double.valueOf(this.context.getMatchValue(parameterValue));
		else
			return false;
		
		return true;
	}

	@Override
	public Map<D, L> classify(DataSet<D, L> data) {
		Map<D, L> map = new HashMap<D, L>();
		
		for (D datum : data) {
			map.put(datum, this.label);
		}
		
		return map;
	}
	
	@Override
	public Map<D, Pair<L, Double>> classifyWithScore(DataSet<D, L> data) {
		Map<D, Pair<L, Double>> map = new HashMap<D, Pair<L, Double>>();
		
		for (D datum : data) {
			map.put(datum, new Pair<L, Double>(this.label, this.score));
		}
		
		return map;
	}

	@Override
	public boolean init(DataSet<D, L> testData) {
		return true;
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
		return "Constant";
	}

	@Override
	public MethodClassification<D, L> clone(String referenceName) {
		MethodClassificationConstant<D, L> clone = new MethodClassificationConstant<D, L>(this.context);
		if (!clone.fromParse(this.getModifiers(), referenceName, toParse()))
			return null;
		return clone;
	}

	@Override
	public MethodClassification<D, L> makeInstance(DatumContext<D, L> context) {
		return new MethodClassificationConstant<D, L>(context);
	}

	@Override
	public boolean hasTrainable() {
		return false;
	}

	@Override
	public Trainable<D, L> getTrainable() {
		return null;
	}

	@Override
	public L classify(D datum) {
		return this.label;
	}

	@Override
	public Pair<L, Double> classifyWithScore(D datum) {
		return new Pair<L, Double>(this.label, this.score);
	}

	@Override
	public Map<D, Double> score(DataSet<D, L> data, L label) {
		Map<D, Double> map = new HashMap<>();
		
		for (D datum : data) {
			map.put(datum, score(datum, label));
		}
		
		return map;
	}

	@Override
	public double score(D datum, L label) {
		if (label.equals(this.label))
			return this.score;
		else
			return 1.0 - this.score;
	}
}
