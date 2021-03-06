package edu.cmu.ml.rtw.generic.structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.cmu.ml.rtw.generic.data.Context;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.CtxParsable;
import edu.cmu.ml.rtw.generic.parse.Obj;
import edu.cmu.ml.rtw.generic.util.Pair;

public class WeightedStructureSequence extends WeightedStructure {
	private Context context;
	private List<CtxParsable> items;
	private List<Pair<Object, Double>> sourcesAndWeights;
	private double totalWeight = 0.0;
	
	private static String[] parameterNames = { };
	
	
	public WeightedStructureSequence() {
		this(null);
	}
	
	public WeightedStructureSequence(Context context) {
		this.items = new ArrayList<CtxParsable>();
		this.sourcesAndWeights = new ArrayList<Pair<Object, Double>>();
		this.context = context;
	}
	
	@Override
	public String[] getParameterNames() {
		return parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		return true;
	}

	public int size() {
		return this.items.size();
	}
	
	public CtxParsable get(int index) {
		return this.items.get(index);
	}
	
	@Override
	public boolean remove(CtxParsable item) {
		int index = this.items.indexOf(item);
		this.items.remove(index);
		this.totalWeight -= this.sourcesAndWeights.remove(index).getSecond();
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public WeightedStructure add(CtxParsable item, double w, Object source, Collection<?> changes) {
		this.items.add(item);
		this.sourcesAndWeights.add(new Pair<>(source, w));
		this.totalWeight += w;
		if (changes != null)
			((Collection<CtxParsable>)changes).add(item);
		return this;
	}

	@Override
	public double getWeight(CtxParsable item) {
		int index = this.items.indexOf(item);
		return this.sourcesAndWeights.get(index).getSecond();
	}

	@Override
	public WeightedStructure merge(WeightedStructure s) {
		if (!(s instanceof WeightedStructureSequence))
			throw new IllegalArgumentException(); 
		
		WeightedStructureSequence seq = (WeightedStructureSequence)s;
		this.items.addAll(seq.items);
		this.sourcesAndWeights.addAll(seq.sourcesAndWeights);
		
		return this;
	}

	@Override
	public WeightedStructure makeInstance(Context context) {
		return new WeightedStructureSequence(context);
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
		return "Sequence";
	}

	@Override
	public List<CtxParsable> toList() {
		return this.items;
	}
	
	public WeightedStructureSequence clone() {
		WeightedStructureSequence seq = new WeightedStructureSequence(this.context);
		if (!seq.fromParse(toParse()))
			return null;
		
		for (int i = 0; i < this.items.size(); i++) {
			seq.items.add(this.items.get(i));
			seq.sourcesAndWeights.add(this.sourcesAndWeights.get(i));
		}
		
		return seq;
	}
	
	@Override
	public int getItemCount() {
		return this.items.size();
	}
	
	@Override
	public double getTotalWeight() {
		return this.totalWeight;
	}

	@Override
	public Object getSource(CtxParsable item) {
		int index = this.items.indexOf(item);
		return this.sourcesAndWeights.get(index).getFirst();
	}
}
