package cpsc503;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.*;

import java.util.regex.*;

public class TwitterWrapper {

	private static final String DATA_DIR = "data";
	//private Twitter twitter;
	String temp = "blah";
    private TwitterStream _twitterStream;
    private LinkedBlockingQueue<Status> _queue;
    List<String> AllTweets =  new ArrayList<String>();
	
	public TwitterWrapper()
	{
		//TwitterFactory tf = new TwitterFactory();
		//twitter = tf.getInstance();
	}
	
	//Ideally we should be knocking off the URLs from the tweet since they don't need to parsed.
	private String filterOutURLFromTweet(final Status status) {
		final String tweet = status.getText();
		final URLEntity[] urlEntities = status.getURLEntities();
		int startOfURL;
		int endOfURL;
		String truncatedTweet = "";
		if(urlEntities.length > 0)
		{
			for(final URLEntity urlEntity: urlEntities){
				startOfURL = urlEntity.getStart();
				endOfURL = urlEntity.getEnd();
				truncatedTweet += tweet.substring(0, startOfURL) + tweet.substring(endOfURL);
			}
			return truncatedTweet;
		}
		else
		{
			return tweet;
		}

	}
	
	// For some reason, some tweets contain two different types of URL's. One that is just a link, and another that is the Twitter link back to the tweet.
	private String removeAllLinks(String commentstr)
    {
        String urlPattern = "((https?|ftp|gopher|telnet|file|Unsure|http):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
        Pattern p = Pattern.compile(urlPattern,Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(commentstr);
        int i = 0;
        while (m.find()) {
            commentstr = commentstr.replaceAll(m.group(i),"").trim();
            i++;
        }
        return commentstr;
    }
	
	public void StartStream(String item)
	{
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true);
        cb.setOAuthConsumerKey("enGR6tKOROitlIRA5jLP2lMfs");
        cb.setOAuthConsumerSecret("ZpKuYEnsaT6uuFakEIrcJ0GTkmta032dqjGevGfXEHk1D41Xim");
        cb.setOAuthAccessToken("4324165395-4PT47juDy3a9cW1nhkCpxBYtRfFISTgKbMErsfH");
        cb.setOAuthAccessTokenSecret("yCDVmKmwqf5OkK2XaXg1SiNhJrGVwZtYTps7bWGl2Dkd6");
        
		this._twitterStream = new TwitterStreamFactory(cb.build()).getInstance();
		
		final StatusListener statusListener = new StatusListener() {
			@Override
			public void onStatus(final Status status) 
			{
				//_queue.offer(status);
				if(status.getLang().equals("en"))
				{
					// Lets do some cleanup, first remove URL's.
					String text = filterOutURLFromTweet(status);
					boolean isRetweet = status.isRetweet();
					if(isRetweet == false)
					{
						text = removeAllLinks(text);
						// Some strings have newline characters in them.
						text = text.replaceAll("(\\r|\\n)", "");
						
						if(text.length() > 10)
						{
								AllTweets.add(text);
						}
					}
				}
			}

			@Override
			public void onDeletionNotice(final StatusDeletionNotice sdn) {
			}

			@Override
			public void onTrackLimitationNotice(final int i) {
			}

			@Override
			public void onScrubGeo(final long l, final long l1) {
			}

			@Override
			public void onStallWarning(final StallWarning stallWarning) {
			}

			@Override
			public void onException(final Exception e) {
			}
		};
		

		//Our usecase computes the sentiments of States of USA based on tweets.
		//So we will apply filter with US bounding box so that we get tweets specific to US only.
		FilterQuery filterQuery = new FilterQuery();
		String filterArray[] = new String[1];
		filterArray[0] = item;
		filterQuery.language(new String[]{"en"});
		filterQuery.track(filterArray);
		this._twitterStream.addListener(statusListener);
		this._twitterStream.filter(filterQuery);
	}
	
	public final void close() {
		this._twitterStream.cleanUp();
		this._twitterStream.shutdown();
	}
	
	public List<String> GetAllTweets(String item, int numTweets)
	{
		int i = 0;
		while(true)
		{
			 try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			 // set a counter just incase twitter is not sending us
			 // tweets any more. 360 will limit us to 30 minutes.
			 if(AllTweets.size() > numTweets || i > 360)
			 {
				 close();
				 break;
			 }
			 i++;
		}
		return AllTweets;
	}
	
	/*public List<String> Query(String itemToQuery, int numTweets) throws IOException, TwitterException
	{
        Query query = new Query(itemToQuery);
        query.setLang("en");
		List<String> allTweets = new ArrayList<String>();	
		List<Status> tweets = new ArrayList<Status>();
		//long firstQueryID = Long.MAX_VALUE;

		long newestId = -1;
		int remainingTweets = numTweets;

		try
		{ 
		  while(remainingTweets > 0)
		  {
		    remainingTweets = numTweets - tweets.size();
		   
		    
		    if(remainingTweets > 100)
		    {
		      query.count(100);
		    }
		    else
		    {
		     query.count(remainingTweets); 
		    }
		    
		    QueryResult result = twitter.search(query);
		    int num = result.getTweets().size();
		    tweets.addAll(result.getTweets());
		    //Status s = tweets.get(tweets.size()-1);
		    

		    newestId = result.getMaxId();
		    query.setSinceId(newestId);

		    //firstQueryID = s.getId();
		    //query.setMaxId(firstQueryID - 1);
		    
		    remainingTweets = numTweets - tweets.size();
		  }
		}
		catch (TwitterException te) 
		{
		  System.out.println("Couldn't connect: " + te);
		} 
		
		for(Status t: tweets)
		{
			allTweets.add(t.getText());
		}
		return allTweets;
	}*/
	
	
	/** Download n of a user's tweets to outFilename. **/
	public void saveTweetsToFile(List<String> tweets, String outFilename, 
			                         boolean newLine) 
			                         throws IOException, TwitterException
	{
		PrintWriter out = new PrintWriter(outFilename);
		for (String t: tweets)
		{
			if (newLine)
			{
				out.print(t.replace("\n", "") + "\n");
			}
			else
			{
				out.print(t.replace("\n", "") + " ");
			}
		}
		System.out.println(String.format(
				"Wrote %d tweets for to file %s.", 
				tweets.size(), outFilename));
		out.close();
	}
	
	/** Downloads all of a user's tweets to two files:
	 * 
	 * 	  ./data/username/username_tweets.txt        (one tweet per line)
	 *    ./data/username/username_tweets_single.txt (all tweets on one line)
	 * 
	 *  Creates the ./data/username directory if necessary.
	 *  
	 *  Set numTweets <= 0 to download all of the user's tweets. **/
	public void downloadTweetsForItem(String item, int numTweets) 
			throws IOException, TwitterException
	{
		/*if (numTweets <= 0) 
		{ 
			numTweets = Integer.MAX_VALUE; 
		}*/
		File userDir = new File(String.format("%s/%s", DATA_DIR, item));
		boolean success = false;
	    try{
	    	 //success = userDir.mkdir();	
	    	 Files.createDirectory(userDir.toPath());
	    } 
	    catch(IOException se)
	    {
	    	System.out.println("****mkdir failed!!!!!!!!");
	    }
		
		String multiLineFile = 
			String.format("%s/%s/%s_tweets.txt", DATA_DIR, item, item);
		String singleLineFile = 
			String.format("%s/%s/%s_tweets_single.txt", DATA_DIR, item, item);
		String multiLineFileRaw = 
				String.format("%s/%s/%s_tweets_multiRaw.txt", DATA_DIR, item, item);
		//item = "@" + item;
		
		List<String> allTweets = GetAllTweets(item, numTweets);//Query(item, numTweets);
		
		// Now do some pre-processing on allTweets.
		// Topic modelling prefers text with stop words removed etc.
		 List<String> filteredTweets =  new ArrayList<String>();

		 BufferedReader f = new BufferedReader(new FileReader("stopwords.txt"));
		 String line = f.readLine();
		 List<String> stopwords = new ArrayList<String>();
		 while(line != null)
		 {
			 stopwords.add(line);
			 line = f.readLine();
		 }
		 f.close();
		 
		item = item.toLowerCase();
		int i = 0;
		while(i < allTweets.size())
		{
			String text = allTweets.get(i);
			
			// First, remove all non alphabetic characters. This removes a lot of junk! hash tags, prices, etc.
			text = text.replaceAll("[^a-zA-Z\\s]", "").toLowerCase();
			
			// first, get rid of our actual search term. we do not want it to come up as a topic,
			// as we already know that we are searching for tweets of that item
			text = text.replace(item, "");  
			
			// Now, remove all words less than a certain length
			text = text.replaceAll("\\b\\w{1,3}\\b\\s?", "");
			
			// The DMM package doesn't do stop word removal! So I have to do this manually.
			for(String word : stopwords){
			    text = text.replace(word+"\\s*", "");
			}
			
			// Again, remove strings that are too short!
			if(text.length() > 14)
			{
				filteredTweets.add(text);
				i++;
			}
			else
			{
				// This string is too short, so it must get removed.
				allTweets.remove(i);
				// don't increment i, since we just deleted this one.
			}
		}
		// Sentiment analysis doesn't need pre-processing.
		saveTweetsToFile(allTweets, multiLineFileRaw,  true);
		
		// But our topics needs some stuff removed.
		saveTweetsToFile(filteredTweets, multiLineFile,   true);
		saveTweetsToFile(filteredTweets, singleLineFile,  false);

	}
}

