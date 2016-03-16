package edu.cmu.ml.rtw.generic.task.classify;

import java.util.HashMap;
import java.util.Map;

import edu.cmu.ml.rtw.generic.data.annotation.Datum;
import edu.cmu.ml.rtw.generic.data.annotation.DatumContext;
import edu.cmu.ml.rtw.generic.data.feature.DataFeatureMatrix;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.Obj;

public class MethodClassificationConstant<D extends Datum<L>, L> extends MethodClassification<D, L> {
	private L label;
	private String[] parameterNames = { "label" };
	
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
		else 
			return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("label"))
			this.label = (parameterValue == null) ? null : this.context.getDatumTools().labelFromString(this.context.getMatchValue(parameterValue));
		else
			return false;
		
		return true;
	}

	@Override
	public Map<D, L> classify(DataFeatureMatrix<D, L> data) {
		Map<D, L> map = new HashMap<D, L>();
		
		for (D datum : data.getData()) {
			map.put(datum, this.label);
		}
		return map;
	}

	@Override
	public boolean init(DataFeatureMatrix<D, L> testData) {
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
	public MethodClassification<D, L> clone() {
		return new MethodClassificationConstant<D, L>(this.context);
	}

	@Override
	public MethodClassification<D, L> makeInstance(DatumContext<D, L> context) {
		return new MethodClassificationConstant<D, L>(context);
	}
}