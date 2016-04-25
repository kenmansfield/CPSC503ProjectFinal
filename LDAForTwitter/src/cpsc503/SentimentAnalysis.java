package cpsc503;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations.SentimentAnnotatedTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

public class SentimentAnalysis {
    static StanfordCoreNLP pipeline;

    public static void init() {
        pipeline = new StanfordCoreNLP("MyPropFile.properties");
    }

    public static int findSentiment(String tweet) {

        int mainSentiment = 0;
        if (tweet != null && tweet.length() > 0) {
            int longest = 0;
            Annotation annotation = pipeline.process(tweet);
            for (CoreMap sentence : annotation
                    .get(CoreAnnotations.SentencesAnnotation.class)) {
                Tree tree = sentence
                        .get(SentimentAnnotatedTree.class);
                int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
                String partText = sentence.toString();
                if (partText.length() > longest) {
                    mainSentiment = sentiment;
                    longest = partText.length();
                }

            }
        }
        return mainSentiment;
    }
    
   public static ArrayList<Integer> getSentimentForFile(String fileName)
   {
	   //numTopics = 50;
	   String line = null;
	   ArrayList<Integer> docList = new ArrayList<Integer>();
       try {
           // FileReader reads text files in the default encoding.
           FileReader fileReader = 
               new FileReader(fileName);

           // Always wrap FileReader in BufferedReader.
           BufferedReader bufferedReader = 
               new BufferedReader(fileReader);

           while((line = bufferedReader.readLine()) != null) {
        	   int sent = findSentiment(line);
        	   docList.add(sent);
        	   
           }   

           // Always close files.
           bufferedReader.close();         
       }
       catch(FileNotFoundException ex) {
           System.out.println(
               "Unable to open file '" + 
               fileName + "'");                
       }
       catch(IOException ex) {
           System.out.println(
               "Error reading file '" 
               + fileName + "'");                  
           // Or we could just do this: 
           // ex.printStackTrace();
       }
	   return docList;
   }
}
