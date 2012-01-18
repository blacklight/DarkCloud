package it.unimore.weblab.darkcloud.infomanager;

import java.io.File;

public abstract class InformationBuilder  {
	public EncodedInformation encode(String obj)
	{
		return null;
	}
	
	public EncodedInformation encode(File obj)
	{
		return null;
	}
	
	public EncodedInformation encode(Object obj)
	{
		return null;
	}
}
