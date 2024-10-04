package gr.ntua.ivml.mint.persistent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import gr.ntua.ivml.mint.db.DB;

public class Project implements SecurityEnabled {
	protected final Logger log = Logger.getLogger(getClass());

	private long dbID;
	private String description;
	private String name;
	private String color;

	
	// any useful stuff in here
	
	/*
	 * Project id lists overlap is interesting
	 * Access rights rely on it.
	 */
	public static List<Integer> sharedIds( Collection<Integer> pl1, Collection<Integer> pl2 ) {
		HashSet<Integer> testSet= new HashSet<>();
		testSet.addAll( pl1 );
		testSet.retainAll( pl2 );
		
		return testSet.stream().collect( Collectors.toList());		
	}
	//
	// 
	//
	
	
	public boolean hasProject( User u ) {
		return u.getProjectIds().contains( (int) this.getDbID() ); 
	}

	public boolean hasProject( Organization org ) {
		return org.getProjectIds().contains( (int) this.getDbID() ); 
	}

	public boolean hasProject(Dataset ds) {
		return ds.getProjectIds().contains( (int) this.getDbID() ); 
	}
	
	public static List<String> toNames( List<Integer> projectIds  ) {
		return DB.getProjectDAO().findAll().stream()
			.filter( p ->  projectIds.contains( p.getDbID()))
			.map( p -> p.getName())
			.collect( Collectors.toList());
	}
	
	public static List<Integer> toIds ( List<String> projectNames ) {
		return DB.getProjectDAO().findAll().stream()
				.filter( p ->  projectNames.contains( p.getName()))
				.map( p -> (int) p.getDbID())
				.collect( Collectors.toList());
	}
	
	public static Optional<Integer> toId( String projectName ) {
		Optional<Project> project = Optional.ofNullable( DB.getProjectDAO().findByName(projectName));
		return project.map( p-> (int) p.getDbID());
	}
	
	//
	//  Boilerplate getset
	//
	public long getDbID() {
		return dbID;
	}

	public void setDbID(long dbID) {
		this.dbID = dbID;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

}
