package cpsc503;

import com.google.gson.JsonObject;
import models.GibbsSamplingLDA;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.topics.*;
import cc.mallet.types.InstanceList;


public class CPSC503Project {

	static final String[] ADDL_STOP_WORDS = {"http", "rt", "https", "bit", "ly"};
	static final String DATA_DIR = "data";
	static final int NUM_WORDS = 20;
	static final int NUM_ITERS = 3000;
	
	/** Create the data loading pipeline **/
	private static ArrayList<Pipe> makePipeList()
	{
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		// Lowercase everything
		pipeList.add(new CharSequenceLowercase());
		// Unicode letters, underscore, and hashtag
		Pattern pat = Pattern.compile("[\\p{L}_#]+");
		pipeList.add(new CharSequence2TokenSequence(pat));
		// Remove stop words
		TokenSequenceRemoveStopwords tsrs = new TokenSequenceRemoveStopwords();
		tsrs.addStopWords(ADDL_STOP_WORDS);
		pipeList.add(tsrs);
		// Convert the token sequence to a feature sequence.
		pipeList.add(new TokenSequence2FeatureSequence());
		return pipeList;
	}
	
	/** Load a file, with one instance per line, 
	 *  and return as an InstanceList. **/
	public static InstanceList fileToInstanceList(String filename)
	{
		InstanceList instances = new InstanceList(new SerialPipes(makePipeList()));
		instances.addThruPipe(new SimpleFileLineIterator(filename));
		return instances;
	}
	
	/** numTopics = 20, alphaT = 1.0, betaW = 0.01 **/
	public static ParallelTopicModel trainModel(InstanceList instances, 
												int numTopics, int numIters,
												double alphaT, double betaW)
	{
		ParallelTopicModel model = new ParallelTopicModel(numTopics, alphaT, betaW);
		model.addInstances(instances);
		model.setNumThreads(2);
		model.setOptimizeInterval(20);
		model.setNumIterations(numIters);
		try
		{
			model.estimate();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return model;
	}
	
	/** Given a trained model, infer the distribution for a single new instance.
	 *  (Only infers for the first element of the input instances). **/
	public static double[] inferTopicDistribution(ParallelTopicModel model,
			  InstanceList instances)
	{
		TopicInferencer inferencer = model.getInferencer();
		double[] distribution = inferencer.getSampledDistribution(instances.get(0), NUM_ITERS, 1, 5);
		for (int i=0; i< distribution.length; i++)
		{
			System.out.println(i + "\t" + distribution[i]);
		}
		return distribution;
	}
	
	public static String[][] getTopWords(ParallelTopicModel model, 
			 					   InstanceList instances,
			 					   int numTopics, int numWords)
	{
		String[][] topWords = new String[numTopics][];
		Alphabet dataAlphabet = instances.getDataAlphabet();
		// Get an array of sorted sets of word ID/count pairs
		ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
		// Show top words in topics with proportions
		for (int topic = 0; topic < numTopics; topic++) 
		{
			String[] words = new String[numWords];
			Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
			int rank = 0;
			while (iterator.hasNext() && rank < numWords) {
				IDSorter idCountPair = iterator.next();
				words[rank] = String.format("%s:%.0f", 
						dataAlphabet.lookupObject(idCountPair.getID()), 
						idCountPair.getWeight());
				rank++;
			}
		topWords[topic] = words;
		}
		return topWords;
	}

	public static String[][] topWordsToFile(String outFile,
								   	  ParallelTopicModel model, 
									  InstanceList instances,
									  int numTopics, int numWords)
	{
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
			String[][] topWords = CPSC503Project.getTopWords(model, instances, numTopics, numWords);
			for (int topic = 0; topic<topWords.length; topic++)
			{
				pw.println(String.format("Topic %d", topic));
				System.out.print(String.format("Topic %d \t", topic));
				for (String word: topWords[topic])
				{
					pw.println(String.format("%s", word));
					System.out.print(String.format("%s ", word));
				}
				pw.println();
				System.out.println();
			}
	        pw.close();
			return topWords;
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public static void distToFile(String outFile, double[] dist)
	{
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
	        for (int i = 0; i < dist.length; i++) {
	            pw.println(i + "\t" + dist[i]);
	        }
	        pw.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static String[][] topTopicsToFile(String outFile,
									   double[] inferredDistribution, 
									   String[][] topWords, 
									   int numTopics)
	{
		String[][] topTopics = new String[numTopics][];
		// get ranked topic indices
		Integer[] idx = new Integer[inferredDistribution.length];
		final double[] data = inferredDistribution;
		for (int i=0; i<idx.length; i++) { idx[i] = i; }
		Arrays.sort(idx, new Comparator<Integer>() {
		    @Override public int compare(Integer o1, Integer o2) {
		        return Double.compare(data[o1], data[o2]);
		    }
		});
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
			for (int i = 0; i<numTopics; i++)
			{
				String[] words = new String[topWords[0].length];
				int topicNum = idx[idx.length - 1 - i];
				pw.println("Topic " + topicNum + ": " + inferredDistribution[topicNum]);
				int j = 0;
				for (String word: topWords[topicNum])
				{
					pw.println(String.format("%s", word));
					words[j] = word;
					j++;
				}
				pw.println();
				topTopics[i] = words;
			}
	        pw.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return topTopics;
	}
	
	/** Assumes folder structure generated by TwitterClient **/
	public static ParallelTopicModel processTweets(String item, int numTopics, List<String []> topWords)
	{
		String multiLineFile = 
				String.format("%s/%s/%s_tweets.txt", DATA_DIR, item, item);
		String singleLineFile = 
			String.format("%s/%s/%s_tweets_single.txt", DATA_DIR, item, item);
		String topWordsFile = 
				String.format("%s/%s/%s_top_words.txt", DATA_DIR, item, item);
		String distFile = 
				String.format("%s/%s/%s_composition.txt", DATA_DIR, item, item);
		String top5File = 
				String.format("%s/%s/%s_ranked.txt", DATA_DIR, item, item);
		//String jsonFile = 
		//		String.format("viz/d3bubble/json/%s.json", username);
		
		InstanceList instances = CPSC503Project.fileToInstanceList(multiLineFile);
		ParallelTopicModel model = CPSC503Project.trainModel(
				instances, numTopics, NUM_ITERS, 1.0, 0.01);
		
	
		String temp[][] = topWordsToFile(topWordsFile, model, instances, numTopics, NUM_WORDS);

	    List<String []> list = Arrays.asList(temp);
	    //List result = new ArrayList();
	    for(String[] array : list){
	    	topWords.add( array) ;
	    }
		
		System.out.println("Inferring overall topic distribution...");
		double[] dist = inferTopicDistribution(model, 
				CPSC503Project.fileToInstanceList(singleLineFile));
		distToFile(distFile, dist);
		String[][] topTopics = topTopicsToFile(
				top5File, 
				dist, 
				getTopWords(model, instances, numTopics, NUM_WORDS), 
				10);
		//toJSON(jsonFile, topTopics);
		return model;
	}
	
	public static double[][] getDocumentTopics(ArrayList<TopicAssignment> data, int numTopics) 
	{
		double[][] result = new double[data.size()][numTopics];

		for (int doc = 0; doc < data.size(); doc++) {
			int[] topics = data.get(doc).topicSequence.getFeatures();
			for (int position = 0; position < topics.length; position++) {
				result[doc][ topics[position] ]++;
			}
		}
		return result;
	}
	
	public void getDocTopics(ParallelTopicModel model)	
	{
		int[] documents = new int[model.data.size()];
		int max = 1;
		//out.print ("#doc name topic proportion ...\n");
		int docLen;
		int[] topicCounts = new int[ model.numTopics ];

		IDSorter[] sortedTopics = new IDSorter[ model.numTopics ];
		for (int topic = 0; topic < model.numTopics; topic++) {
			// Initialize the sorters with dummy values
			sortedTopics[topic] = new IDSorter(topic, topic);
		}

		if (max < 0 || max > model.numTopics) 
		{
			max = model.numTopics;
		}

		for (int doc = 0; doc < model.data.size(); doc++) 
		{
			LabelSequence topicSequence = (LabelSequence) model.data.get(doc).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();

			StringBuilder builder = new StringBuilder();

			builder.append(doc);
			builder.append("\t");

			if (model.data.get(doc).instance.getName() != null) {
				builder.append(model.data.get(doc).instance.getName()); 
			}
			else {
				builder.append("no-name");
			}

			builder.append("\t");
			docLen = currentDocTopics.length;

			// Count up the tokens
			for (int token=0; token < docLen; token++) {
				topicCounts[ currentDocTopics[token] ]++;
			}

			// And normalize
			for (int topic = 0; topic < model.numTopics; topic++) {
				sortedTopics[topic].set(topic, (model.alpha[topic] + topicCounts[topic]) / (docLen + model.alphaSum) );
			}
			
			Arrays.sort(sortedTopics);

			for (int i = 0; i < max; i++) 
			{
				documents[doc] = sortedTopics[i].getID();
				//if (sortedTopics[i].getWeight() < threshold) { break; }
				
				builder.append(sortedTopics[i].getID() + "\t" + 
							   sortedTopics[i].getWeight() + "\t");
			}
			//out.println(builder);
			System.out.println(builder);

			//Arrays.fill(topicCounts, 0);
		}
	}
		
	public static ArrayList<TreeSet<IDSorter>> getTopicDocuments(double smoothing, int numTopics, ArrayList<TopicAssignment> data) {
		ArrayList<TreeSet<IDSorter>> topicSortedDocuments = new ArrayList<TreeSet<IDSorter>>(numTopics);

		// Initialize the tree sets
		for (int topic = 0; topic < numTopics; topic++) {
			topicSortedDocuments.add(new TreeSet<IDSorter>());
		}

		int[] topicCounts = new int[numTopics];

		for (int doc = 0; doc < data.size(); doc++) {
			int[] topics = data.get(doc).topicSequence.getFeatures();
			for (int position = 0; position < topics.length; position++) {
				topicCounts[ topics[position] ]++;
			}

			for (int topic = 0; topic < numTopics; topic++) {
				topicSortedDocuments.get(topic).add(new IDSorter(doc, (topicCounts[topic] + smoothing) / (topics.length + numTopics * smoothing) ));
				topicCounts[topic] = 0;
			}
		}

		return topicSortedDocuments;
	}

	public static void toJSON(String outFile, String[][] topWords)
	{
		JsonObject jo = new JsonObject();
		jo.addProperty("name", "");
		JsonArray ja = new JsonArray();
		for (int i = 0; i<topWords.length; i++)
		{
			String[] topic = topWords[i];
			JsonObject jTopic = new JsonObject();
			jTopic.addProperty("name", topic[0].split(":")[0]);
			JsonArray jWordsArray = new JsonArray();
			for (String word: topic)
			{
				JsonObject jWord = new JsonObject();
				String[] toks = word.split(":");
				jWord.addProperty("name", toks[0]);
				jWord.addProperty("size", Integer.parseInt(toks[1]));
				jWordsArray.add(jWord);
			}
			jTopic.add("children", jWordsArray);
			ja.add(jTopic);
		}
		jo.add("children", ja);
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
			pw.println(jo.toString());
			pw.close();
		} catch (IOException e) { e.printStackTrace(); }
	}
	
	public static int[] getDocTopics2(ParallelTopicModel model)
	{
		int threshold = 0;
		int [] docTopics = new int[model.data.size()];
		
		// Just want one document.
		int max = 1;
		int docLen;
		int[] topicCounts = new int[ model.numTopics ];

		IDSorter[] sortedTopics = new IDSorter[ model.numTopics ];
		for (int topic = 0; topic < model.numTopics; topic++) {
			// Initialize the sorters with dummy values
			sortedTopics[topic] = new IDSorter(topic, topic);
		}

		for (int doc = 0; doc < model.data.size(); doc++) {
			LabelSequence topicSequence = (LabelSequence) model.data.get(doc).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();

			docLen = currentDocTopics.length;

			// Count up the tokens
			for (int token=0; token < docLen; token++) {
				topicCounts[ currentDocTopics[token] ]++;
			}

			// And normalize
			for (int topic = 0; topic < model.numTopics; topic++) {
				sortedTopics[topic].set(topic, (model.alpha[topic] + topicCounts[topic]) / (docLen + model.alphaSum) );
			}
			
			Arrays.sort(sortedTopics);

			for (int i = 0; i < max; i++) {
				if (sortedTopics[i].getWeight() < threshold) { break; }
				docTopics[doc] = sortedTopics[i].getID();
				
				// builder.append(sortedTopics[i].getID() + "\t" +   sortedTopics[i].getWeight() + "\t");
			}

			Arrays.fill(topicCounts, 0);
		}		
		return docTopics;
	}
	
	
	public static void main(String[] args) throws Exception 
	{
		// Can change this to come in as an argument
		String item = "brexit";
		
		String multiLineFile = 
				String.format("%s/%s/%s_tweets.txt", DATA_DIR, item, item);
		String multiLineFileRaw = 
				String.format("%s/%s/%s_tweets_multiRaw.txt", DATA_DIR, item, item);
		
		int numTopics = 50;
		int numTweets = 1000;

		// 1. Get our document set.
		TwitterWrapper tW = new TwitterWrapper();

		// Using the Search API. doesn't return us enough tweets, so use the Streaming API instead.
		//tW.Query(item, numTweets);
		
		tW.StartStream(item);
		tW.downloadTweetsForItem(item, numTweets);
		
		List<String []> topWords = new ArrayList<String []>();

		// 2. Get the topics for all the documents.
		ParallelTopicModel model = processTweets(item, numTopics, topWords);
		int[] docTopics = getDocTopics2(model);
	
		DMM(multiLineFile, numTopics);

		// 3. Get the sentiment for each document
		// Run this on every document.
		SentimentAnalysis.init();
		ArrayList<Integer> docSentiments = SentimentAnalysis.getSentimentForFile(multiLineFileRaw);
		
		// Create our aggregate topic scores!
		double[] topicSent = new double[numTopics];
		for(int i = 0; i < numTopics; i++)
		{
			int numSents = 0;
			for(int doc = 0; doc < docSentiments.size(); doc++)
			{
				int topic = docTopics[doc];
				if(topic == i)
				{
					numSents++;
					topicSent[topic] += (double)(docSentiments.get(doc));
				}
			}
			topicSent[i] /= (double)numSents;
		}
		String finalFile = DATA_DIR + "/" + item + "/" + item + "_final.txt";
		PrintWriter pw = new PrintWriter(finalFile);
		
		for(int i = 0; i < topicSent.length; i++)
		{
			double sent = topicSent[i];
			pw.print("Topic " + i + ": ");
			
			int x = 0;
			for(String word : topWords.get(i))
			{
				// Lets only show a maximum of 4 words.
				if(x < 4)
				{
					pw.print(word);
					pw.print(" ");
					x++;
				}
			}
			pw.print("\nScore: ");
			pw.print(sent);
			pw.print("\n");
		}
		pw.close();
		System.out.println("Result written to file " + finalFile);
	}
	
	public static void DMM(String textFile, int numTopics) throws Exception
	{
		// This is the one topic per document DMM using gibbs sampling.
        GibbsSamplingLDA lda = new GibbsSamplingLDA(textFile, numTopics,
                .1, 0.1, 2000, 20,
                "theModel", "", 0);
        lda.inference();
	}

}
