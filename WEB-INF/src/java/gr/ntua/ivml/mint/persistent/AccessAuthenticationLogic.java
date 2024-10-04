package gr.ntua.ivml.mint.persistent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.apache.log4j.Logger;

import gr.ntua.ivml.mint.api.ParameterException;
/**
 * Current implementation is based on specific actions are allowed
 * not allowed for users on objects.
 * 
 * Other implementations could go and check against the database 
 * or access tokens on the User etc.
 * 
 * Write one function per action.
 * Spaces in the actions are replaced with _
 * @author Arne Stabenau
 *
 */
public class AccessAuthenticationLogic {
	static final Logger log = Logger.getLogger(AccessAuthenticationLogic.class );
	
	public static enum Access {
		NONE, READ, WRITE, PROJECT, OWN;
		
		public boolean isAtLeast( Access other ) {
			return ( this.compareTo(other)>=0 );
		}
		
		public boolean isWorseThan( Access other ) {
			return ( this.compareTo(other)<0 );			
		}
	}
	
	/**
	 * Super users can do everything, otherwise the request for authentication is 
	 * delegated to the action method.
	 * @param u
	 * @param se
	 * @param action
	 * @return
	 */
	public static boolean can( User u, SecurityEnabled se, String action ) {
		boolean result = false;
		if( u.getRights() == User.SUPER_USER) return true;
		if( se == null ) {
			log.info( "Authentication " +action+ " is missing argument" );
			return false;
		}
		result = dispatch(u, se, action.replace(" ", "_"));
		log.debug( action + " " + (result?"yes":"no" ));
		return result;
	}
	
	
	// a function for every action that needs it, maybe some actions are caught in 
	// the beginning
	

	/**
	 * Only super users can make super users. Everybody else
	 * gets a false from this function.
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_make_super_user( User u, SecurityEnabled se ) {
		return false;
	}
	
	private static boolean action_server_file_access( User u, SecurityEnabled se  ) {
		return false;
	}
	
	private static boolean action_download( User u, SecurityEnabled se ) {
		try {
			DataUpload du = (DataUpload) se;
			if( du.getCreator().getDbID() == u.getDbID()) return true;
			Organization o = du.getOrganization();
			return ( belongs( u, o) && 
					(((u.getRights()&(User.ADMIN|User.PUBLISH|User.MODIFY_DATA))!=0)));
		} catch( Exception e ) {
			log.info( "download needs DataUplaod as argument", e );
		}
		return false;
	}
	

	
	/**
	 * se is Organization data belongs to
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_change_data( User u, SecurityEnabled se ) {
		try {
			Organization o = (Organization) se;
			return ( belongs( u, o) && ( u.hasRight(User.MODIFY_DATA) || ( u.hasRight( User.ADMIN))));
		} catch( Exception e ) {
			log.info( "change data needs Organization as argument" ,e );
		}
		return false;
	}

	/**
	 * se is Organization data belongs to
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_view_data( User u, SecurityEnabled se ) {
		try {
			if( se instanceof Organization) {
				Organization o = (Organization) se;
				return ( belongs( u, o) && (u.getRights()>0));
			}
			if( se instanceof Dataset) {
				Organization o = ((Dataset) se).getOrganization();
				if ( belongs( u, o) && (u.getRights()>0)) return true;
				if( u.sharesProject(((Dataset)se).getOrigin())) return true;
				return false;
			}
		} catch( Exception e ) {
			log.info( "change data needs Organization as argument" ,e );
		}
		return false;
	}

	
	/**
	 * se is organization to be modified
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_modify_organization( User u, SecurityEnabled se ) {
		try {
			Organization o = (Organization) se;
			return ( belongs( u, o) && u.hasRight(User.ADMIN));
		} catch( Exception e ) {
			log.info( "modify organization needs Organization as argument" ,e );
		}
		return false;
	}
	/**
	 * se is organization the data belongs to
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_publish_data( User u, SecurityEnabled se ) {
		try {
			Organization o = (Organization) se;
			return ( belongs( u, o) && u.hasRight(User.PUBLISH));
		} catch( Exception e ) {
			log.info( "publish data needs Organization as argument" ,e );
		}
		return false;
	}
	
	/**
	 * se is organization the data belongs to
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_view_unpublished( User u, SecurityEnabled se ) {
		try {
			Organization o = (Organization) se;
			return belongs( u, o);
		} catch( Exception e ) {
			log.info( "view unpublished needs Organization as argument" ,e );
		}
		return false;
	}
	
	/**
	 * se is user to be modified
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_modify_user( User u, SecurityEnabled se ) {
		try {
			User u2 = (User) se;
			if( u2.getDbID() == u.getDbID()) return true;
			return ( belongs( u, u2.getOrganization()) && u.hasRight(User.ADMIN));
		} catch( Exception e ) {
			log.info( "modify user needs User as argument" ,e );
		}
		return false;
	}

	
	/**
	 * se is user to be modified. Call for stuff the user is not supposed to do on himself
	 * @param u
	 * @param se
	 * @return
	 */
	private static boolean action_admin_user( User u, SecurityEnabled se ) {
		try {
			User u2 = (User) se;
			return ( belongs( u, u2.getOrganization()) && u.hasRight(User.ADMIN));
		} catch( Exception e ) {
			log.info( "change rights needs User as argument" ,e );
		}
		return false;
	}

	
	private static boolean belongs( User u, Organization o ) {
		if( o == null ) return false;
		do {
			if( u.getOrganization().getDbID() == o.getDbID())
				return true;
		} while(( o=o.getParentalOrganization()) != null );			
		return false;
	}
	

	private static boolean dispatch( User u, SecurityEnabled se, String action ) {
		try {
			Method[] methods = AccessAuthenticationLogic.class.getDeclaredMethods();
			for( Method m: methods ) {
				if( Modifier.isStatic( m.getModifiers()) ) {
					if( m.getName().startsWith("action_")) {
						if(m.getName().endsWith(action)) {
							// call it
							Boolean b = (Boolean) m.invoke(null, u, se );
							log.debug( "Invoked " + action);
							return b.booleanValue();
						}
					}
				}
			}
			log.warn( "No such action " +action);
			return false;
		} catch( Exception e ) {
			log.debug( e );
		}
		return false;
	}
	/*
	 * Method to check if a user has access to a specific item
	 * Applies to items that implement AccessCheckEnabled marker interface.
	 * 
	 * Here is the logic in informal terms. If the user is in the org with the item
	 *  the rights are the user rights.
	 * 
	 *  If the user is project user, the item might share project with the user, then the 
	 *  right is PROJECT
	 *  
	 *  If the owner of item is not in the org, the right has to be project
	 *  
	 *  project and own differs in the fact that owner can assign project, project access can not 
	 *  assign or remove project.
	 */
	public static Access getAccess(AccessCheckEnabled item, User u) {
		if( u.getRights() == User.SUPER_USER) return Access.OWN;
		Access access = Access.NONE;
		
		Organization org=null;
		boolean sharesProject=false, created=false;
		
		if (item instanceof Dataset) {
			Dataset ds = (Dataset) item;
			if( ds.getCreator().getDbID() == u.getDbID()) created=true;
			if( u.sharesProject(ds.getOrigin())) sharesProject=true;
			org = ds.getOrganization();
		}
		else if (item instanceof Organization) {
			org = (Organization) item;
			if( u.sharesProject(org)) sharesProject=true;
		}
		else if (item instanceof Enrichment) {
			Enrichment enrichment = (Enrichment) item;
			org = enrichment.getOrganization();
			if( enrichment.getCreator().getDbID() == u.getDbID()) created=true;
			if( u.sharesProject(enrichment)) sharesProject=true;
		} else if( item instanceof Translation ) {
			Translation tl = (Translation) item;
			if( tl.getCreator().getDbID() == u.getDbID()) created = true;
			if( ! Project.sharedIds( u.getProjectIds(), tl.getProjectIds()).isEmpty()) sharesProject=true;
		}

		if( belongs(u, org)) {
			if(( u.getRights() & ( User.PUBLISH | User.VIEW_DATA )) > 0 ) access = Access.READ;
			if(( u.getRights() &  User.ADMIN ) > 0 ) access = Access.OWN;
			if(( u.getRights() &  User.MODIFY_DATA ) > 0 ) access = Access.WRITE;
			if( created ) access = Access.OWN;  
		} 
		if( sharesProject && access.isWorseThan(Access.WRITE)) access=Access.PROJECT;

		return access;
	}
	
	public static void checkAccess( AccessCheckEnabled item, User u, Access requestedAccess ) throws ParameterException {
		if( getAccess( item, u).isWorseThan(requestedAccess)) throw new ParameterException( "Access denied");
	}
}
