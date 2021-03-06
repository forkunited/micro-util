package edu.cmu.ml.rtw.generic.task.classify;

import java.util.Arrays;
import java.util.Map;

import edu.cmu.ml.rtw.generic.data.annotation.DataSet;
import edu.cmu.ml.rtw.generic.data.annotation.Datum;
import edu.cmu.ml.rtw.generic.data.annotation.Datum.Tools.DatumIndicator;
import edu.cmu.ml.rtw.generic.data.annotation.DatumContext;
import edu.cmu.ml.rtw.generic.parse.Assignment;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.Obj;
import edu.cmu.ml.rtw.generic.util.Pair;

public class MethodClassificationFilterDatumIndicator<D extends Datum<L>, L> extends MethodClassification<D, L> implements Trainable<D, L> {
	private DatumIndicator<D> datumIndicator; 
	private MethodClassification<D, L> method;
	private String[] parameterNames = { "datumIndicator", "method" };
	
	public MethodClassificationFilterDatumIndicator() {
		this(null);
	}
	
	public MethodClassificationFilterDatumIndicator(DatumContext<D, L> context) {
		super(context);
	}
	
	public MethodClassification<D, L> getInnerMethod() {
		return this.method;
	}

	@Override
	public String[] getParameterNames() {
		if (this.method != null) {
			String[] parameterNames = Arrays.copyOf(this.parameterNames, this.parameterNames.length + this.method.getParameterNames().length);
			for (int i = 0; i < this.method.getParameterNames().length; i++)
				parameterNames[this.parameterNames.length + i] = this.method.getParameterNames()[i];
			return parameterNames;
		} else 
			return this.parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("datumIndicator"))
			return (this.datumIndicator == null) ? null : Obj.stringValue(this.datumIndicator.toString());
		else if (parameter.equals("method"))
			return (this.method == null) ? null :  Obj.curlyBracedValue(this.method.getReferenceName());
		else if (this.method != null)
			return this.method.getParameterValue(parameter);
		else 
			return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("datumIndicator")) {
			this.datumIndicator = (parameterValue == null) ? null : this.context.getDatumTools().getDatumIndicator(this.context.getMatchValue(parameterValue));
		} else if (parameter.equals("method"))
			this.method = (parameterValue == null) ? null : this.context.getMatchClassifyMethod(parameterValue);
		else if (this.method != null)
			return this.method.setParameterValue(parameter, parameterValue);
		else
			return false;
		
		return true;
	}

	@Override
	public Map<D, L> classify(DataSet<D, L> data) {
		DataSet<D, L> filteredData = data.filter(this.datumIndicator, this.context.getMaxThreads());
		return this.method.classify(filteredData);
	}
	
	@Override
	public Map<D, Pair<L, Double>> classifyWithScore(DataSet<D, L> data) {
		DataSet<D, L> filteredData = data.filter(this.datumIndicator, this.context.getMaxThreads());
		return this.method.classifyWithScore(filteredData);
	}

	@Override
	public boolean init(DataSet<D, L> testData) {
		DataSet<D, L> filteredData = testData.filter(this.datumIndicator, this.context.getMaxThreads());
		return this.method.init(filteredData);
	}

	@Override
	protected boolean fromParseInternal(AssignmentList internalAssignments) {
		return true;
	}

	@Override
	protected AssignmentList toParseInternal() {
		AssignmentList assignments = new AssignmentList();
		if (this.method != null)
			assignments.add(Assignment.assignmentTyped(null, "classify_method", "method", this.method.toParse(true)));
		return assignments;
	}

	@Override
	public String getGenericName() {
		return "FilterDatumIndicator";
	}

	@Override
	public MethodClassification<D, L> clone(String referenceName) {
		MethodClassificationFilterDatumIndicator<D, L> clone = new MethodClassificationFilterDatumIndicator<D, L>(this.context);
		if (!clone.fromParse(this.getModifiers(), this.getReferenceName(), toParse()))
			return null;
		clone.method = this.method.clone();
		clone.referenceName = referenceName;
		return clone;
	}

	@Override
	public MethodClassification<D, L> makeInstance(DatumContext<D, L> context) {
		return new MethodClassificationFilterDatumIndicator<D, L>(context);
	}

	@Override
	public boolean hasTrainable() {
		return this.method.hasTrainable();
	}

	@Override
	public Trainable<D, L> getTrainable() {
		if (this.method.hasTrainable())
			return this;
		else 
			return null;
	}

	@Override
	public boolean train() {
		if (!hasTrainable())
			return false;
		return this.method.getTrainable().train();
	}

	@Override
	public boolean setTrainData(DataSet<D, L> data) {
		if (!hasTrainable())
			return false;
		
		return this.method.getTrainable().setTrainData(
				data.filter(this.datumIndicator, this.context.getMaxThreads()));
	}

	@Override
	public boolean setDevData(DataSet<D, L> data) {
		if (!hasTrainable())
			return false;
		
		return this.method.getTrainable().setDevData(
				data.filter(this.datumIndicator, this.context.getMaxThreads()));
	}

	@Override
	public boolean iterateTraining(Map<D, L> constrainedData) {
		if (!hasTrainable())
			return false;
		return this.method.getTrainable().iterateTraining(constrainedData);
	}

	@Override
	public DataSet<D, L> getTrainData() {
		return this.method.getTrainable().getTrainData();
	}

	@Override
	public L classify(D datum) {
		if (this.datumIndicator.indicator(datum))
			return this.method.classify(datum);
		else
			return null;
	}

	@Override
	public Pair<L, Double> classifyWithScore(D datum) {
		if (this.datumIndicator.indicator(datum))
			return this.method.classifyWithScore(datum);
		else
			return null;
	}

	@Override
	public Map<D, Double> score(DataSet<D, L> data, L label) {
		DataSet<D, L> filteredData = data.filter(this.datumIndicator, this.context.getMaxThreads());
		return this.method.score(filteredData, label);
	}

	@Override
	public double score(D datum, L label) {
		if (this.datumIndicator.indicator(datum))
			return this.method.score(datum, label);
		else
			return 0.0;
	}
}
