package edu.cmu.ml.rtw.generic.structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import edu.cmu.ml.rtw.generic.data.Context;
import edu.cmu.ml.rtw.generic.data.feature.fn.Fn;
import edu.cmu.ml.rtw.generic.parse.AssignmentList;
import edu.cmu.ml.rtw.generic.parse.CtxParsable;
import edu.cmu.ml.rtw.generic.parse.Obj;
import edu.cmu.ml.rtw.generic.rule.RuleSet;
import edu.cmu.ml.rtw.generic.util.Pair;
import edu.cmu.ml.rtw.generic.util.ThreadMapper;
import edu.cmu.ml.rtw.generic.util.Triple;

public class FnGreedyStructureRules<S extends WeightedStructure> extends FnStructure<S, S> {
	private static final double EPSILON = .1;
	
	private Context context;
	
	private Obj.Array rulesRefs;
	private List<RuleSet> rules;
	private Obj.Array splitFnsRefs;
	private List<FnStructure<S, ?>> splitFns;
	private int maxIterations = 0;
	private boolean addInOrder = true;
	private int maxIterationSize = -1;
	private String[] parameterNames = { "rules", "splitFns", "maxIterations", "addInOrder", "maxIterationSize" };
	
	public FnGreedyStructureRules() {
		
	}
	
	public FnGreedyStructureRules(Context context) {
		this.context = context;
	}
	
	@Override
	public String[] getParameterNames() {
		return parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("rules")) {
			return (this.rulesRefs == null) ? null : this.rulesRefs;
		} else if (parameter.equals("splitFns")) {
			return (this.splitFnsRefs == null) ? null : this.splitFnsRefs;
		} else if (parameter.equals("maxIterations")) {
			return Obj.stringValue(String.valueOf(this.maxIterations));
		} else if (parameter.equals("addInOrder")) {
			return Obj.stringValue(String.valueOf(this.addInOrder));
		} else if (parameter.equals("maxIterationSize")) {
			return Obj.stringValue(String.valueOf(this.maxIterationSize));
		} else 
			return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("rules")) {
			this.rulesRefs = (Obj.Array)parameterValue;
			if (this.rulesRefs != null) {
				this.rules = new ArrayList<>();
				for (int i = 0; i < this.rulesRefs.size(); i++)
					this.rules.add(this.context.getMatchRuleSet(this.rulesRefs.get(i)));
			} else {
				this.rules = null;
			}
		} else if (parameter.equals("splitFns")) {
			this.splitFnsRefs = (Obj.Array)parameterValue;
			if (this.splitFnsRefs != null) {
				this.splitFns = new ArrayList<>();
				for (int i = 0; i < this.splitFnsRefs.size(); i++)
					this.splitFns.add((FnStructure<S, ?>)this.context.getMatchStructureFn(this.splitFnsRefs.get(i)));
			} else {
				this.splitFns = null;
			}
		} else if (parameter.equals("maxIterations")) {
			this.maxIterations = Integer.valueOf(this.context.getMatchValue(parameterValue));
		} else if (parameter.equals("addInOrder")) {
			this.addInOrder = Boolean.valueOf(this.context.getMatchValue(parameterValue));
		} else if (parameter.equals("maxIterationSize")) {
			this.maxIterationSize = Integer.valueOf(this.context.getMatchValue(parameterValue));
		} else 
			return false;
		return true;
	}

	@Override
	protected <C extends Collection<S>, F extends WeightedStructure> C compute(Collection<S> input, C output, Collection<F> filter) {
		if (this.addInOrder)
			return computeAddInOrder(input, output, filter);
		else
			return computeThreaded(input, output, filter);
			
	}
	
	private <C extends Collection<S>, F extends WeightedStructure> C computeAddInOrder(Collection<S> input, C output, Collection<F> filter) {
		for (S structure : input) {
			int iterations = 0;
			int prevFilterSize = (filter != null) ? filter.size() : 0;
			double weightChange = 0;
			do {
				int iterFilterSize = (filter != null) ? filter.size() : 0;
				if (this.maxIterationSize > 0 && iterFilterSize > this.maxIterationSize) {
					this.context.getDataTools().getOutputWriter().debugWriteln("Greedy inference iteration " + iterations + " size exceeded max (" + iterFilterSize + ") choosing max weighted subset");
					List<F> filterList = new ArrayList<>();
					filterList.addAll(filter);
					Collections.sort(filterList, new Comparator<F>() {
						@Override
						public int compare(F o1, F o2) {
							// FIXME Note this will break if structure doesn't contain filter items
							return Double.compare(structure.getWeight(o2), structure.getWeight(o1));
						}
					});
					
					filter.clear();
					for (int i = 0; i < this.maxIterationSize; i++) {
						filter.add(filterList.get(i));
					}
				}
				
				//long startTime = System.currentTimeMillis();
					
				List<Triple<List<CtxParsable>, Double, Integer>> orderedStructureParts = new ArrayList<Triple<List<CtxParsable>, Double, Integer>>();
				for (int i = 0; i < this.splitFns.size(); i++) {
					FnStructure<S, ?> splitFn = this.splitFns.get(i);
					
					List<?> splitStructure = splitFn.listCompute(structure, filter);
					
					for (Object o : splitStructure) {
						WeightedStructure structurePart = (WeightedStructure)o;
						List<CtxParsable> structurePartList = structurePart.toList();
						double weight = 0.0;
						
						for (CtxParsable part : structurePartList) {
							weight += structurePart.getWeight(part);
						}
						orderedStructureParts.add(new Triple<>(structurePartList, weight / (double)structurePartList.size(), i));
					}
				}
				
				Collections.sort(orderedStructureParts, new Comparator<Pair<List<CtxParsable>, Double>>() {
					@Override
					public int compare(Pair<List<CtxParsable>, Double> o1, Pair<List<CtxParsable>, Double> o2) {
						return Double.compare(o2.getSecond(), o1.getSecond());
					}
					
				});
				
				prevFilterSize = (filter != null) ? filter.size() : 0;
				filter = new HashSet<F>();
				double totalWeight = structure.getTotalWeight();
				
				//this.context.getDataTools().getOutputWriter().debugWriteln("Greedy inference running on iteration " + iterations + " trying to add " + orderedStructureParts.size() + " to graph ");
				
				for (Triple<List<CtxParsable>, Double, Integer> structurePart : orderedStructureParts) {
					Map<String, List<Obj>> objs = this.rules.get(structurePart.getThird()).apply(structurePart.getFirst());
					for (Entry<String, List<Obj>> objList : objs.entrySet())
						for (Obj obj : objList.getValue()) {
							WeightedStructure newStructurePart = this.context.constructMatchWeightedStructure(obj);
							structure.add(newStructurePart, structurePart.getSecond(), filter);
						}
				}
				
				//this.context.getDataTools().getOutputWriter().debugWriteln("Greedy inference running iteration " + iterations + " on size " + iterFilterSize + " (" + (System.currentTimeMillis() - startTime) + ")");
				
				iterations++;
				weightChange = structure.getTotalWeight() - totalWeight;
			} while ((this.maxIterations == 0 || iterations <= this.maxIterations) && filter.size() > 0 && (filter.size() != prevFilterSize || weightChange > EPSILON));
			
			output.add(structure);
		}
		
		return output;
	}

	@SuppressWarnings("unchecked")
	private <C extends Collection<S>, F extends WeightedStructure> C computeThreaded(Collection<S> input, C output, Collection<F> filter) {
		for (S structure : input) {
			int iterations = 0;
			int prevFilterSize = (filter != null) ? filter.size() : 0;
			double weightChange = 0;
			do {
				long curTime = System.currentTimeMillis();
				this.context.getDataTools().getOutputWriter().debugWriteln("Running structure rules iteration " + iterations + " split." );
				List<Triple<List<CtxParsable>, Double, Integer>> structureParts = new ArrayList<Triple<List<CtxParsable>, Double, Integer>>();
				for (int i = 0; i < this.splitFns.size(); i++) {
					FnStructure<S, ?> splitFn = this.splitFns.get(i);
					List<?> splitStructure = splitFn.listCompute(structure, filter);
					final int splitFnIndex = i;
					ThreadMapper<Object, Boolean> mapper = new ThreadMapper<Object, Boolean>(
						new ThreadMapper.Fn<Object, Boolean>() {
							@Override
							public Boolean apply(Object o) {
								WeightedStructure structurePart = (WeightedStructure)o;
								List<CtxParsable> structurePartList = structurePart.toList();
								double weight = 0.0;
								
								for (CtxParsable part : structurePartList) {
									weight += structurePart.getWeight(part);
								}
								
								synchronized (structureParts) {
									structureParts.add(new Triple<>(structurePartList, weight / (double)structurePartList.size(), splitFnIndex));
								}
								
								return true;
							}
						}
					);
					
					mapper.run((List<Object>)splitStructure, this.context.getMaxThreads(), true);
				}
				
				this.context.getDataTools().getOutputWriter().debugWriteln("Finished structure rules iteration " + iterations + " split. (" + (System.currentTimeMillis() - curTime) + ")" );
				curTime = System.currentTimeMillis();
				this.context.getDataTools().getOutputWriter().debugWriteln("Running structure rules iteration " + iterations + " rule application." );
				
				
				prevFilterSize = (filter != null) ? filter.size() : 0;
				
				double totalWeight = structure.getTotalWeight();
				
				final Set<F> tempFilter = new HashSet<F>();
				List<Pair<WeightedStructure, Double>> newSortedStructureParts = new ArrayList<>();
				ThreadMapper<Triple<List<CtxParsable>, Double, Integer>, Boolean> mapper = new ThreadMapper<Triple<List<CtxParsable>, Double, Integer>, Boolean>(new ThreadMapper.Fn<Triple<List<CtxParsable>, Double, Integer>, Boolean>() {
					@Override
					public Boolean apply(Triple<List<CtxParsable>, Double, Integer> structurePart) {
						Map<String, List<Obj>> objs = rules.get(structurePart.getThird()).apply(structurePart.getFirst());
						for (Entry<String, List<Obj>> objList : objs.entrySet()) {
							for (Obj obj : objList.getValue()) {
								WeightedStructure newStructurePart = context.constructMatchWeightedStructure(obj);
								synchronized (newSortedStructureParts) {
									newSortedStructureParts.add(new Pair<WeightedStructure, Double>(newStructurePart, structurePart.getSecond()));
								}
							}
						}
						
						return true;
					}
				});
				mapper.run(structureParts, this.context.getMaxThreads(), true);
				
				this.context.getDataTools().getOutputWriter().debugWriteln("Finished structure rules iteration " + iterations + " rule application (" + (System.currentTimeMillis() - curTime) + ")");
				curTime = System.currentTimeMillis();
				this.context.getDataTools().getOutputWriter().debugWriteln("Running rules iteration " + iterations + " sorting new structure parts");
				
				Collections.sort(newSortedStructureParts, new Comparator<Pair<WeightedStructure, Double>>() {
					@Override
					public int compare(Pair<WeightedStructure, Double> o1, Pair<WeightedStructure, Double> o2) {
						return Double.compare(o2.getSecond(), o1.getSecond());
					}
				});
				
				for (Pair<WeightedStructure, Double> pair : newSortedStructureParts)
					structure.add(pair.getFirst(), pair.getSecond(), tempFilter);
				
				this.context.getDataTools().getOutputWriter().debugWriteln("Finished structure rules iteration " + iterations + " sorting new structure parts (" + (System.currentTimeMillis() - curTime) + ")");

				filter = tempFilter;
				iterations++;
				weightChange = structure.getTotalWeight() - totalWeight;
			} while ((this.maxIterations == 0 || iterations <= this.maxIterations) && filter.size() > 0 && (filter.size() != prevFilterSize || weightChange > EPSILON));
			
			output.add(structure);
		}
		
		return output;
	}
	
	@Override
	public Fn<S, S> makeInstance(Context context) {
		return new FnGreedyStructureRules<S>(context);
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
		return "GreedyStructureRules";
	}
}
