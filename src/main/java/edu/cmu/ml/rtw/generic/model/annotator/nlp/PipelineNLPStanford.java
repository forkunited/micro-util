package edu.cmu.ml.rtw.generic.model.annotator.nlp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.Map.Entry;

import edu.cmu.ml.rtw.generic.data.StoredItemSet;
import edu.cmu.ml.rtw.generic.data.annotation.AnnotationType;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.AnnotationTypeNLP;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.ConstituencyParse;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.DependencyParse;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.DocumentNLP;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.DocumentNLPMutable;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.PoSTag;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.Token;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.TokenSpan;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.TokenSpanCluster;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.ConstituencyParse.Constituent;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.DependencyParse.Dependency;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.DependencyParse.Node;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.time.NormalizedTimeValue;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.time.TimeExpression;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.time.TimeExpression.TimeMLDocumentFunction;
import edu.cmu.ml.rtw.generic.data.annotation.nlp.time.TimeExpression.TimeMLType;
import edu.cmu.ml.rtw.generic.data.store.StoreReference;
import edu.cmu.ml.rtw.generic.util.Pair;
import edu.cmu.ml.rtw.generic.util.Triple;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.CorefChain.CorefMention;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.time.SUTime.Temporal;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;

/**
 * PipelineNLPStanford is a PipelineNLP wrapper for the
 * Stanford CoreNLP pipeline.
 * 
 * @author Bill McDowell
 *
 */
public class PipelineNLPStanford extends PipelineNLP {
	private abstract class AnnotatorTokenStanford<T> implements AnnotatorToken<T> {
		public abstract Pair<T, Double>[][] annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices);
	}
	
	private abstract class AnnotatorSentenceStanford<T> implements AnnotatorSentence<T> {
		public abstract Map<Integer, Pair<T, Double>> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices);
	}
	
	private abstract class AnnotatorTokenSpanStanford<T> implements AnnotatorTokenSpan<T> {
		public abstract List<Triple<TokenSpan, T, Double>> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices);
	}
	
	private abstract class AnnotatorDocumentStanford<T> implements AnnotatorToken<T> {
		public abstract Pair<T, Double> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices);
	}
	
	
	private StanfordCoreNLP nlpPipeline;
	private int maxSentenceLength;
	
	public PipelineNLPStanford() {
		super();
		this.maxSentenceLength = 0;
	}
	
	public PipelineNLPStanford(int maxSentenceLength) {
		super();
		this.maxSentenceLength = maxSentenceLength;
	}
	
	public PipelineNLPStanford(PipelineNLPStanford pipeline) {
		super();
		
		this.nlpPipeline = pipeline.nlpPipeline;
		this.maxSentenceLength = pipeline.maxSentenceLength;
		
		for (AnnotationType<?> annotationType : pipeline.annotationOrder)
			addAnnotator(annotationType);
	}
	
	public synchronized boolean initialize() {
		return initialize(null);
	}
	
	public synchronized  boolean initialize(AnnotationTypeNLP<?> disableFrom) {
		return initialize(disableFrom, null, null, null);
	}
	
	public synchronized boolean initialize(AnnotationTypeNLP<?> disableFrom, Annotator tokenizer) {
		return initialize(disableFrom, tokenizer, null, null);
	}
	
	public synchronized boolean initialize(AnnotationTypeNLP<?> disableFrom, Annotator tokenizer, 
							  StoredItemSet<TimeExpression, TimeExpression> storedTimexes, 
							  StoredItemSet<NormalizedTimeValue, NormalizedTimeValue> storedTimeValues) {
                return initialize(disableFrom, tokenizer, storedTimexes, storedTimeValues, null, false);
        }

	public synchronized boolean initialize(AnnotationTypeNLP<?> disableFrom, Annotator tokenizer, 
							  StoredItemSet<TimeExpression, TimeExpression> storedTimexes, 
                                                          StoredItemSet<NormalizedTimeValue, NormalizedTimeValue> storedTimeValues,
                                                          Properties props,
                                                          boolean enableCleanXML) {
                if (props == null) props = new Properties();
		
		if (tokenizer != null) {
			if (!tokenizer.requirementsSatisfied().containsAll(Annotator.TOKENIZE_AND_SSPLIT))
				return false;
			
			String tokenizerClass = tokenizer.getClass().getName();
		    props.put("customAnnotatorClass.tokenize", tokenizerClass);
		    props.put("customAnnotatorClass.ssplit", tokenizerClass);
		}
		
		String propsStr = "";
		if (disableFrom == null) {
			propsStr = "tokenize, ssplit, pos, lemma, parse, ner, dcoref";
		} else if (disableFrom.equals(AnnotationTypeNLP.TOKEN)) {
			throw new IllegalArgumentException("Can't disable tokenization");
		} else if (disableFrom.equals(AnnotationTypeNLP.POS)) {
			propsStr = "tokenize, ssplit";
		} else if (disableFrom.equals(AnnotationTypeNLP.LEMMA)) {
			propsStr = "tokenize, ssplit, pos";
		} else if (disableFrom.equals(AnnotationTypeNLP.CONSTITUENCY_PARSE)) {
			propsStr = "tokenize, ssplit, pos, lemma";
		} else if (disableFrom.equals(AnnotationTypeNLP.DEPENDENCY_PARSE)) {
			propsStr = "tokenize, ssplit, pos, lemma, parse";
		} else if (disableFrom.equals(AnnotationTypeNLP.NER)) {
			propsStr = "tokenize, ssplit, pos, lemma, parse";
		} else if (disableFrom.equals(AnnotationTypeNLP.COREF)) {
			propsStr = "tokenize, ssplit, pos, lemma, parse, ner";
		}
                if (enableCleanXML) propsStr.replace("tokenize, ", "tokenize, cleanxml, ");

		if (this.maxSentenceLength != 0) {
			props.put("pos.maxlen", String.valueOf(this.maxSentenceLength));
			props.put("parse.maxlen", String.valueOf(this.maxSentenceLength));
		}
		
		props.put("annotators", propsStr);
		this.nlpPipeline = new StanfordCoreNLP(props);
		
		if (storedTimexes != null && storedTimeValues != null) {
			this.nlpPipeline.addAnnotator(new TimeAnnotator("sutime", props));
		}
		
		clearAnnotators();
		
		if (!addAnnotator(AnnotationTypeNLP.TOKEN))
			return false;
		
		if (disableFrom != null && disableFrom.equals(AnnotationTypeNLP.POS))
			return true;
		
		if (!addAnnotator(AnnotationTypeNLP.POS))
			return false;
		
		if (disableFrom != null && disableFrom.equals(AnnotationTypeNLP.LEMMA))
			return true;

		if (!addAnnotator(AnnotationTypeNLP.LEMMA))
			return false;
		
		if (disableFrom != null && disableFrom.equals(AnnotationTypeNLP.CONSTITUENCY_PARSE))
			return true;
		
		if (!addAnnotator(AnnotationTypeNLP.CONSTITUENCY_PARSE))
			return false;
		
		if (disableFrom != null && disableFrom.equals(AnnotationTypeNLP.DEPENDENCY_PARSE))
			return true;
		
		if (!addAnnotator(AnnotationTypeNLP.DEPENDENCY_PARSE))
			return false;
	
		if (disableFrom != null && disableFrom.equals(AnnotationTypeNLP.NER))
			return true;
		
		if (!addAnnotator(AnnotationTypeNLP.NER))
			return false;
		
		if (disableFrom == null || !disableFrom.equals(AnnotationTypeNLP.COREF)) {
			if (!addAnnotator(AnnotationTypeNLP.COREF))
				return false;
		}
		
		if (storedTimexes == null || storedTimeValues == null) {
			return true;
		}
		
		if (!addTimexAnnotator(storedTimexes, storedTimeValues))
			return false;
		
		return true;
	}
	
	
	private int getValidSentenceIndex(int sentenceIndex, int[] originalToValidSentenceIndices) {
		if (originalToValidSentenceIndices == null)
			return sentenceIndex;
		else
			return originalToValidSentenceIndices[sentenceIndex];
	}
	
	private boolean addAnnotator(AnnotationType<?> annotationType) {
		if (annotationType.equals(AnnotationTypeNLP.TOKEN)) {
			addAnnotator(AnnotationTypeNLP.TOKEN,  new AnnotatorTokenStanford<Token>() {
				public String getName() { return "stanford_3.6.0"; }
				public AnnotationType<Token> produces() { return AnnotationTypeNLP.TOKEN; };
				public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.ORIGINAL_TEXT }; }
				public boolean measuresConfidence() { return false; }
				@SuppressWarnings("unchecked")
				@Override
				public Pair<Token, Double>[][] annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
					List<CoreMap> sentences = annotatedText.get(SentencesAnnotation.class);
					Pair<Token, Double>[][] tokens = (Pair<Token, Double>[][])(new Pair[validSentenceCount][]);
					for(int i = 0; i < sentences.size(); i++) {
						List<CoreLabel> sentenceTokens = sentences.get(i).get(TokensAnnotation.class);
						int validSentenceIndex = getValidSentenceIndex(i, originalToValidSentenceIndices);
						if (validSentenceIndex < 0)
							continue;
						
						tokens[validSentenceIndex] = (Pair<Token, Double>[])new Pair[sentenceTokens.size()];
						for (int j = 0; j < sentenceTokens.size(); j++) {
							String word = sentenceTokens.get(j).get(TextAnnotation.class); 
							int charSpanStart = sentenceTokens.get(j).beginPosition();
							int charSpanEnd = sentenceTokens.get(j).endPosition();
							tokens[validSentenceIndex][j] = new Pair<Token, Double>(new Token(document, word, charSpanStart, charSpanEnd), null);
						}
					}
					
					return tokens;
				}
				@Override
				public Pair<Token, Double>[][] annotate(DocumentNLP document) {
					throw new UnsupportedOperationException();
				}
			});
			
			return true;
		} else if (annotationType.equals(AnnotationTypeNLP.POS)) {
			addAnnotator(AnnotationTypeNLP.POS,  new AnnotatorTokenStanford<PoSTag>() {
				public String getName() { return "stanford_3.6.0"; }
				public AnnotationType<PoSTag> produces() { return AnnotationTypeNLP.POS; };
				public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.TOKEN }; }
				public boolean measuresConfidence() { return false; }
				@SuppressWarnings("unchecked")
				public Pair<PoSTag, Double>[][] annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
					List<CoreMap> sentences = annotatedText.get(SentencesAnnotation.class);
					Pair<PoSTag, Double>[][] posTags = (Pair<PoSTag, Double>[][])new Pair[validSentenceCount][];
					
					for (int i = 0; i < sentences.size(); i++) {
						List<CoreLabel> sentenceTokens = sentences.get(i).get(TokensAnnotation.class);
						int validSentenceIndex = getValidSentenceIndex(i, originalToValidSentenceIndices);
						if (validSentenceIndex < 0)
							continue;
						
						posTags[validSentenceIndex] = (Pair<PoSTag, Double>[])new Pair[sentenceTokens.size()];
						for (int j = 0; j < sentenceTokens.size(); j++) {
							String pos = sentenceTokens.get(j).get(PartOfSpeechAnnotation.class);  
							
							if (pos.length() > 0 && !Character.isLetter(pos.toCharArray()[0]))
								posTags[validSentenceIndex][j] = new Pair<PoSTag, Double>(PoSTag.SYM, null);
							else
								posTags[validSentenceIndex][j] = new Pair<PoSTag, Double>(PoSTag.valueOf(pos), null);
						}
					}
					
					return posTags;
				}
				
				@Override
				public Pair<PoSTag, Double>[][] annotate(DocumentNLP document) {		
					throw new UnsupportedOperationException();
				}
			});
			
			return true;
		} else if (annotationType.equals(AnnotationTypeNLP.LEMMA)) {
			addAnnotator(AnnotationTypeNLP.LEMMA,  new AnnotatorTokenStanford<String>() {
				public String getName() { return "stanford_3.6.0"; }
				public AnnotationType<String> produces() { return AnnotationTypeNLP.LEMMA; };
				public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.TOKEN, AnnotationTypeNLP.POS }; }
				public boolean measuresConfidence() { return false; }
				@SuppressWarnings("unchecked")
				public Pair<String, Double>[][] annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
					List<CoreMap> sentences = annotatedText.get(SentencesAnnotation.class);
					Pair<String, Double>[][] lemmas = (Pair<String, Double>[][])new Pair[validSentenceCount][];
					
					for (int i = 0; i < sentences.size(); i++) {
						List<CoreLabel> sentenceTokens = sentences.get(i).get(TokensAnnotation.class);
						int validSentenceIndex = getValidSentenceIndex(i, originalToValidSentenceIndices);
						if (validSentenceIndex < 0)
							continue;
						
						lemmas[validSentenceIndex] = (Pair<String, Double>[])new Pair[sentenceTokens.size()];
						for (int j = 0; j < sentenceTokens.size(); j++) {
							String lemma = sentenceTokens.get(j).get(LemmaAnnotation.class);  
							lemmas[validSentenceIndex][j] = new Pair<String, Double>(lemma, null);
						}
					}
					
					return lemmas;
				}
				@Override
				public Pair<String, Double>[][] annotate(DocumentNLP document) {
					throw new UnsupportedOperationException();
				}
			});
		
			return true;
		} else if (annotationType.equals(AnnotationTypeNLP.CONSTITUENCY_PARSE)) {
			addAnnotator(AnnotationTypeNLP.CONSTITUENCY_PARSE,  new AnnotatorSentenceStanford<ConstituencyParse>() {
				public String getName() { return "stanford_3.6.0"; }
				public AnnotationType<ConstituencyParse> produces() { return AnnotationTypeNLP.CONSTITUENCY_PARSE; };
				public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.TOKEN, AnnotationTypeNLP.POS }; }
				public boolean measuresConfidence() { return false; }
				public Map<Integer, Pair<ConstituencyParse, Double>> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
					List<CoreMap> sentences = annotatedText.get(SentencesAnnotation.class);
					Map<Integer, Pair<ConstituencyParse, Double>> parses = new HashMap<Integer, Pair<ConstituencyParse, Double>>();

					for(int i = 0; i < sentences.size(); i++) {
						int validSentenceIndex = getValidSentenceIndex(i, originalToValidSentenceIndices);
						if (validSentenceIndex < 0)
							continue;
						
						Tree tree = sentences.get(i).get(TreeAnnotation.class);
	
						Constituent root = null;
						parses.put(validSentenceIndex, new Pair<ConstituencyParse, Double>(new ConstituencyParse(document, validSentenceIndex, null), null));
						
						if (tree == null)
							continue;
						
						Stack<Pair<Tree, List<Constituent>>> constituents = new Stack<Pair<Tree, List<Constituent>>>();
						Stack<Tree> toVisit = new Stack<Tree>();
						toVisit.push(tree);
						int tokenIndex = 0;
						while (!toVisit.isEmpty()) {
							Tree currentTree = toVisit.pop();
							
							if (!constituents.isEmpty()) {
								while (!isStanfordTreeParent(currentTree, constituents.peek().getFirst())) {
									Pair<Tree, List<Constituent>> currentNeighbor = constituents.pop();
									ConstituencyParse.Constituent constituent = parses.get(validSentenceIndex).getFirst().new Constituent(currentNeighbor.getFirst().label().value(), currentNeighbor.getSecond().toArray(new ConstituencyParse.Constituent[0]));
									constituents.peek().getSecond().add(constituent);
								}
							}
							
							if (currentTree.isPreTerminal()) {
								String label = currentTree.label().value();
								ConstituencyParse.Constituent constituent = parses.get(validSentenceIndex).getFirst().new Constituent(label, new TokenSpan(document, validSentenceIndex, tokenIndex, tokenIndex + 1));
								tokenIndex++;
								if (!constituents.isEmpty())
									constituents.peek().getSecond().add(constituent);
								else
									root = constituent;
							} else {
								constituents.push(new Pair<Tree, List<Constituent>>(currentTree, new ArrayList<Constituent>()));
								for (int j = currentTree.numChildren() - 1; j >= 0; j--)
									toVisit.push(currentTree.getChild(j));
							}
						}
						
						while (!constituents.isEmpty()) {
							Pair<Tree, List<Constituent>> possibleRoot = constituents.pop();
							root = parses.get(validSentenceIndex).getFirst().new Constituent(possibleRoot.getFirst().label().value(), possibleRoot.getSecond().toArray(new ConstituencyParse.Constituent[0]));
							if (!constituents.isEmpty())
								constituents.peek().getSecond().add(root);
						}
						
						parses.put(validSentenceIndex, new Pair<ConstituencyParse, Double>(new ConstituencyParse(document, validSentenceIndex, root), null));
					}
					
					return parses;
				}
				
				private boolean isStanfordTreeParent(Tree tree, Tree possibleParent) {
					for (int j = 0; j < possibleParent.numChildren(); j++) {
						if (possibleParent.getChild(j).equals(tree)) {
							return true;
						}
					}
					return false;
				}
				
				@Override
				public Map<Integer, Pair<ConstituencyParse, Double>> annotate(DocumentNLP document) {
					throw new UnsupportedOperationException();
				}
				
			});
		
			return true;
		} else if (annotationType.equals(AnnotationTypeNLP.DEPENDENCY_PARSE)) {
			addAnnotator(AnnotationTypeNLP.DEPENDENCY_PARSE,  new AnnotatorSentenceStanford<DependencyParse>() {
				public String getName() { return "stanford_3.6.0"; }
				public AnnotationType<DependencyParse> produces() { return AnnotationTypeNLP.DEPENDENCY_PARSE; };
				public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.TOKEN, AnnotationTypeNLP.POS, AnnotationTypeNLP.CONSTITUENCY_PARSE }; }
				public boolean measuresConfidence() { return false; }
				public Map<Integer, Pair<DependencyParse, Double>> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
					List<CoreMap> sentences = annotatedText.get(SentencesAnnotation.class);
					Map<Integer, Pair<DependencyParse, Double>> parses = new HashMap<Integer, Pair<DependencyParse, Double>>();
					for(int i = 0; i < sentences.size(); i++) {
						int validSentenceIndex = getValidSentenceIndex(i, originalToValidSentenceIndices);
						if (validSentenceIndex < 0)
							continue;
						
						SemanticGraph sentenceDependencyGraph = sentences.get(i).get(CollapsedCCProcessedDependenciesAnnotation.class);
						
						Set<IndexedWord> sentenceWords = sentenceDependencyGraph.vertexSet();
						
						Map<Integer, Pair<List<DependencyParse.Dependency>, List<DependencyParse.Dependency>>> nodesToDeps = new HashMap<Integer, Pair<List<DependencyParse.Dependency>, List<DependencyParse.Dependency>>>();
						parses.put(validSentenceIndex, new Pair<DependencyParse, Double>(new DependencyParse(document, validSentenceIndex, null, null), null));
						int maxIndex = -1;
						for (IndexedWord sentenceWord1 : sentenceWords) {
							for (IndexedWord sentenceWord2 : sentenceWords) {
								if (sentenceWord1.equals(sentenceWord2))
									continue;
								GrammaticalRelation relation = sentenceDependencyGraph.reln(sentenceWord1, sentenceWord2);
								if (relation == null)
									continue;
							
								int govIndex = sentenceWord1.index() - 1;
								int depIndex = sentenceWord2.index() - 1;
								
								maxIndex = Math.max(depIndex, Math.max(govIndex, maxIndex));
								
								DependencyParse.Dependency dependency = parses.get(validSentenceIndex).getFirst().new Dependency(govIndex, depIndex, relation.getShortName());
								
								if (!nodesToDeps.containsKey(govIndex))
									nodesToDeps.put(govIndex, new Pair<List<Dependency>, List<Dependency>>(new ArrayList<Dependency>(), new ArrayList<Dependency>()));
								if (!nodesToDeps.containsKey(depIndex))
									nodesToDeps.put(depIndex, new Pair<List<Dependency>, List<Dependency>>(new ArrayList<Dependency>(), new ArrayList<Dependency>()));
								
								nodesToDeps.get(govIndex).getSecond().add(dependency);
								nodesToDeps.get(depIndex).getFirst().add(dependency);
							}
						}
						
						if (!nodesToDeps.containsKey(-1))
							nodesToDeps.put(-1, new Pair<List<Dependency>, List<Dependency>>(new ArrayList<Dependency>(), new ArrayList<Dependency>()));
						
						
						Collection<IndexedWord> rootDeps = sentenceDependencyGraph.getRoots();
						for (IndexedWord rootDep : rootDeps) {
							int depIndex = rootDep.index() - 1;
							DependencyParse.Dependency dependency = parses.get(validSentenceIndex).getFirst().new Dependency(-1, depIndex, "root");
							
							if (!nodesToDeps.containsKey(depIndex))
								nodesToDeps.put(depIndex, new Pair<List<Dependency>, List<Dependency>>(new ArrayList<Dependency>(), new ArrayList<Dependency>()));
							
							nodesToDeps.get(-1).getSecond().add(dependency);
							nodesToDeps.get(depIndex).getFirst().add(dependency);
						}
						
						Node[] tokenNodes = new Node[maxIndex+1];
						for (int j = 0; j < tokenNodes.length; j++)
							if (nodesToDeps.containsKey(j))
								tokenNodes[j] = parses.get(validSentenceIndex).getFirst().new Node(j, nodesToDeps.get(j).getFirst().toArray(new Dependency[0]), nodesToDeps.get(j).getSecond().toArray(new Dependency[0]));
						
						Node rootNode = parses.get(validSentenceIndex).getFirst().new Node(-1, new Dependency[0], nodesToDeps.get(-1).getSecond().toArray(new Dependency[0]));
						parses.put(validSentenceIndex, new Pair<DependencyParse, Double>(new DependencyParse(document, validSentenceIndex, rootNode, tokenNodes), null));
					}
					
					return parses;
				}
				@Override
				public Map<Integer, Pair<DependencyParse, Double>> annotate(DocumentNLP document) {
					throw new UnsupportedOperationException();
				}
			});
			
			return true;
		} else if (annotationType.equals(AnnotationTypeNLP.NER)) {
			addAnnotator(AnnotationTypeNLP.NER,  new AnnotatorTokenSpanStanford<String>() {
				public String getName() { return "stanford_3.6.0"; }
				public AnnotationType<String> produces() { return AnnotationTypeNLP.NER; };
				public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.TOKEN, AnnotationTypeNLP.POS, AnnotationTypeNLP.CONSTITUENCY_PARSE, AnnotationTypeNLP.DEPENDENCY_PARSE }; }
				public boolean measuresConfidence() { return false; }
				public List<Triple<TokenSpan, String, Double>> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
					// FIXME Don't need to do this in a two step process where construct
					// array and then convert it into token span list.  This was just refactored
					// from old code in a rush, but can be done more efficiently
					
					List<CoreMap> sentences = annotatedText.get(SentencesAnnotation.class);
					String[][] ner = new String[sentences.size()][];
					for(int i = 0; i < sentences.size(); i++) {
						int validSentenceIndex = getValidSentenceIndex(i, originalToValidSentenceIndices);
						if (validSentenceIndex < 0)
							continue;
						
						List<CoreLabel> sentenceTokens = sentences.get(i).get(TokensAnnotation.class);
						
						ner[i] = new String[sentenceTokens.size()];
						for (int j = 0; j < sentenceTokens.size(); j++) {
							ner[i][j] = sentenceTokens.get(j).get(NamedEntityTagAnnotation.class); 
						}
					}
					
					List<Triple<TokenSpan, String, Double>> nerAnnotations = new ArrayList<Triple<TokenSpan, String, Double>>();
					for (int i = 0; i < ner.length; i++) {
						int validSentenceIndex = getValidSentenceIndex(i, originalToValidSentenceIndices);
						if (validSentenceIndex < 0)
							continue;
						
						for (int j = 0; j < ner[i].length; j++) {
							if (ner[i][j] != null) {
								int endTokenIndex = j + 1;
								for (int k = j + 1; k < ner[i].length; k++) {
									if (ner[i][k] == null || !ner[i][k].equals(ner[i][j])) {
										endTokenIndex = k;
										break;
									}
									ner[i][k] = null;
								}
								
								nerAnnotations.add(new Triple<TokenSpan, String, Double>(new TokenSpan(document, validSentenceIndex, j, endTokenIndex), ner[i][j], null));
							}
						}
					}
					
					return nerAnnotations;
				}
				@Override
				public List<Triple<TokenSpan, String, Double>> annotate(DocumentNLP document) {
					throw new UnsupportedOperationException();
				}
			});
			
			return true;
		} else if (annotationType.equals(AnnotationTypeNLP.COREF)) {
			addAnnotator(AnnotationTypeNLP.COREF,  new AnnotatorTokenSpanStanford<TokenSpanCluster>() {
				public String getName() { return "stanford_3.6.0"; }
				public AnnotationType<TokenSpanCluster> produces() { return AnnotationTypeNLP.COREF; };
				public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.TOKEN, AnnotationTypeNLP.POS, AnnotationTypeNLP.CONSTITUENCY_PARSE, AnnotationTypeNLP.DEPENDENCY_PARSE, AnnotationTypeNLP.NER }; }
				public boolean measuresConfidence() { return false; }
				public List<Triple<TokenSpan, TokenSpanCluster, Double>> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
					Map<Integer, CorefChain> corefGraph = annotatedText.get(CorefChainAnnotation.class);
					List<Triple<TokenSpan, TokenSpanCluster, Double>> annotations = new ArrayList<Triple<TokenSpan, TokenSpanCluster, Double>>();
					
					for (Entry<Integer, CorefChain> entry : corefGraph.entrySet()) {
						CorefChain corefChain = entry.getValue();
						CorefMention representativeMention = corefChain.getRepresentativeMention();
						int representativeSentIndex = getValidSentenceIndex(representativeMention.sentNum - 1, originalToValidSentenceIndices);
						if (representativeSentIndex < 0)
							continue;
						
						TokenSpan representativeSpan = new TokenSpan(document, 
																	 representativeSentIndex,
																	 representativeMention.startIndex - 1,
																	 representativeMention.endIndex - 1);
						
						List<TokenSpan> spans = new ArrayList<TokenSpan>();
						Map<IntPair, Set<CorefMention>> mentionMap = corefChain.getMentionMap();
						for (Entry<IntPair, Set<CorefMention>> spanEntry : mentionMap.entrySet()) {
							for (CorefMention mention : spanEntry.getValue()) {
								int validSentenceIndex = getValidSentenceIndex(mention.sentNum - 1, originalToValidSentenceIndices);
								if (validSentenceIndex < 0)
									continue;
								
								spans.add(new TokenSpan(document,
															validSentenceIndex,
															mention.startIndex - 1,
															mention.endIndex - 1));
							}
						}
						
						TokenSpanCluster cluster = new TokenSpanCluster(entry.getKey(), representativeSpan, spans);
						for (TokenSpan span : spans)
							annotations.add(new Triple<TokenSpan, TokenSpanCluster, Double>(span, cluster, null));
					}
					
					return annotations;
				}
				
				@Override
				public List<Triple<TokenSpan, TokenSpanCluster, Double>> annotate(DocumentNLP document) {
					throw new UnsupportedOperationException();
				}
			});
			
			return true;
		}
		
		return false;
	}
	
	private boolean addTimexAnnotator(StoredItemSet<TimeExpression, TimeExpression> storedTimexes, StoredItemSet<NormalizedTimeValue, NormalizedTimeValue> storedTimeValues) {
		addAnnotator(AnnotationTypeNLP.TIME_EXPRESSION,  new AnnotatorTokenSpanStanford<TimeExpression>() {
			public String getName() { return "stanford_3.6.0"; }
			public AnnotationType<TimeExpression> produces() { return AnnotationTypeNLP.TIME_EXPRESSION; };
			public AnnotationType<?>[] requires() { return new AnnotationType<?>[] { AnnotationTypeNLP.TOKEN, AnnotationTypeNLP.POS }; }
			public boolean measuresConfidence() { return false; }
			public List<Triple<TokenSpan, TimeExpression, Double>> annotateStanford(DocumentNLP document, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
				List<Triple<TokenSpan, TimeExpression, Double>> annotations = new ArrayList<>();
				List<CoreMap> timexAnnsAll = annotatedText.get(TimeAnnotations.TimexAnnotations.class);
				for (CoreMap cm : timexAnnsAll) {
					List<CoreLabel> tokens = cm.get(CoreAnnotations.TokensAnnotation.class);
					if (tokens.get(0).sentIndex() != tokens.get(tokens.size() - 1).sentIndex())
						continue;
					
					int sentenceIndex = getValidSentenceIndex(tokens.get(0).sentIndex(), originalToValidSentenceIndices);
					int startTokenIndex = tokens.get(0).index() - 1;
					int endTokenIndex = tokens.get(tokens.size() - 1).index();
					TokenSpan span = new TokenSpan(document, sentenceIndex, startTokenIndex, endTokenIndex);
					
					String timexId = String.valueOf(document.getDataTools().getIncrementId());
					String valueId = String.valueOf(document.getDataTools().getIncrementId());
					
					StoreReference timexRef = new StoreReference(storedTimexes.getStoredItems().getStorageName(), storedTimexes.getName(), "id", String.valueOf(timexId));
					StoreReference valueRef = new StoreReference(storedTimeValues.getStoredItems().getStorageName(), storedTimeValues.getName(), "id", String.valueOf(valueId));
					
					List<StoreReference> timexRefs = new ArrayList<>();
					timexRefs.add(timexRef);
					
					Temporal temporal = cm.get(edu.stanford.nlp.time.TimeExpression.Annotation.class).getTemporal();
					String valueStr = temporal.getTimexValue();
					NormalizedTimeValue value = new NormalizedTimeValue(document.getDataTools(), 
														    			valueRef, 
														    			valueId, 
														    			valueStr, 
														    			timexRefs);
			        
			        TimeExpression timex = new TimeExpression(document.getDataTools(), 
							  timexRef,
							  span,
							  timexId,
							  "",
							  TimeMLType.TIME,
							  null,
							  null,
							  null,
							  null,
							  valueRef,
							  TimeMLDocumentFunction.NONE,
							  false,
							  null,
							  null,
							  null);
			        
			        storedTimexes.addItem(timex);
			        storedTimeValues.addItem(value);
			        
			        annotations.add(new Triple<TokenSpan, TimeExpression, Double>(span, timex, 1.0));
				}
			
				return annotations;
			}
			
			@Override
			public List<Triple<TokenSpan, TimeExpression, Double>> annotate(DocumentNLP document) {
				throw new UnsupportedOperationException();
			}
		});
		
		return true;
	}

	@Override
	public DocumentNLPMutable run(DocumentNLPMutable document, Collection<AnnotationType<?>> skipAnnotators) {
		if (this.nlpPipeline == null)
			if (!initialize())
				return null;
		
		Annotation annotatedText = new Annotation(document.getOriginalText());
		
		if (document.hasAnnotationType(AnnotationTypeNLP.CREATION_TIME)) {
			annotatedText.set(CoreAnnotations.DocDateAnnotation.class, 			
						document.getDocumentAnnotation(AnnotationTypeNLP.CREATION_TIME).getValue().getValue());
		}
		
		this.nlpPipeline.annotate(annotatedText);
		
		List<CoreMap> sentences = annotatedText.get(SentencesAnnotation.class);
		
		
		int validSentenceCount = 0;
		int[] originalToValidSentenceIndices = null;
		if (this.maxSentenceLength == 0) {
			validSentenceCount = sentences.size();
			originalToValidSentenceIndices = null;
		} else {
			validSentenceCount = 0;
			originalToValidSentenceIndices = new int[sentences.size()];
			for(int i = 0; i < sentences.size(); i++) {
				List<CoreLabel> sentenceTokens = sentences.get(i).get(TokensAnnotation.class);
				if (sentenceTokens.size() <= this.maxSentenceLength) {
					originalToValidSentenceIndices[i] = validSentenceCount;
					validSentenceCount++;
				} else {
					originalToValidSentenceIndices[i] = -1;
				}
			}
		}
		
		return runHelper(document, skipAnnotators, annotatedText, validSentenceCount, originalToValidSentenceIndices);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private DocumentNLPMutable runHelper(DocumentNLPMutable document, Collection<AnnotationType<?>> skipAnnotators, Annotation annotatedText, int validSentenceCount, int[] originalToValidSentenceIndices) {
		for (int annotatorIndex = 0; annotatorIndex < getAnnotatorCount(); annotatorIndex++) {
			AnnotationTypeNLP<?> annotationType = (AnnotationTypeNLP<?>)getAnnotationType(annotatorIndex);
			if (skipAnnotators != null && skipAnnotators.contains(annotationType)) {
				continue;
			}

			if (!meetsAnnotatorRequirements(annotationType, document)) {
				throw new UnsupportedOperationException("Document does not meet annotation type requirements for " + annotationType.getType() + " annotator.");
			}
				
			if (annotationType.getTarget() == AnnotationTypeNLP.Target.DOCUMENT) {
				document.setDocumentAnnotation(this.annotators.get(annotationType).getName(), annotationType, ((AnnotatorDocumentStanford<?>)this.annotators.get(annotationType)).annotateStanford(document, annotatedText, validSentenceCount, originalToValidSentenceIndices));
			} else if (annotationType.getTarget() == AnnotationTypeNLP.Target.SENTENCE) {
				document.setSentenceAnnotation(this.annotators.get(annotationType).getName(), annotationType, ((AnnotatorSentenceStanford<?>)this.annotators.get(annotationType)).annotateStanford(document, annotatedText, validSentenceCount, originalToValidSentenceIndices));
			} else if (annotationType.getTarget() == AnnotationTypeNLP.Target.TOKEN_SPAN) {
				document.setTokenSpanAnnotation(this.annotators.get(annotationType).getName(), annotationType, ((AnnotatorTokenSpanStanford)this.annotators.get(annotationType)).annotateStanford(document, annotatedText, validSentenceCount, originalToValidSentenceIndices));			
			} else if (annotationType.getTarget() == AnnotationTypeNLP.Target.TOKEN) {
				document.setTokenAnnotation(this.annotators.get(annotationType).getName(), annotationType, ((AnnotatorTokenStanford<?>)this.annotators.get(annotationType)).annotateStanford(document, annotatedText, validSentenceCount, originalToValidSentenceIndices));
			}
		}
		
		return document;
	}
}
