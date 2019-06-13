/*
 * Copyright 2018-2019 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.ena.webin.cli.manifest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

public class
ManifestFieldDefinition 
{

  private final String name;
  private final String description;
  private final ManifestFieldType type;
  private final int minCount;
  private final int maxCount;
  private final int spreadsheetMinCount;
  private final int spreadsheetMaxCount;
  private final List<ManifestFieldProcessor> processors;
  private final String default_value;

  private 
  ManifestFieldDefinition( String name,
                           String description,
                           ManifestFieldType type,
                           int minCount,
                           int maxCount,
                           int spreadsheetMinCount,
                           int spreadsheetMaxCount,
                           List<ManifestFieldProcessor> processors,
                           String default_value ) 
  {
    Assert.notNull( name, "Field name must not be null" );
    Assert.notNull( description, "Field description must not be null" );
    Assert.notNull( type, "Field type must not be null" );
    this.name = name;
    this.description = description;
    this.type = type;
    this.minCount = minCount;
    this.maxCount = maxCount;
    this.spreadsheetMinCount = spreadsheetMinCount;
    this.spreadsheetMaxCount = spreadsheetMaxCount;
    this.processors = processors;
    this.default_value = default_value;
  }
  

  public String 
  getName() 
  {
    return name;
  }

  
  public String 
  getDescription() 
  {
    return description;
  }

  
  public ManifestFieldType 
  getType() 
  {
    return type;
  }

  
  public int 
  getMinCount() 
  {
    return minCount;
  }

  
  public int 
  getMaxCount() 
  {
    return maxCount;
  }

  
  public int 
  getSpreadsheetMinCount() 
  {
    return spreadsheetMinCount;
  }

  
  public int 
  getSpreadsheetMaxCount() 
  {
    return spreadsheetMaxCount;
  }

  
  public List<ManifestFieldProcessor> 
  getFieldProcessors() 
  {
    return processors;
  }

  
  public String
  getDefaultValue() 
  {
      return default_value;
  }
  
  
  public static class 
  Builder 
  {
    private final List<ManifestFieldDefinition> fields = new ArrayList<>();

    public Field 
    meta() 
    {
      return new Field( this, ManifestFieldType.MANIFEST_META );
    }

    
    public Field 
    file() 
    {
      return new Field( this, ManifestFieldType.MANIFEST_FILE );
    }

    
    public Field 
    data() 
    {
      return new Field( this, ManifestFieldType.VALIDATOR_META ).optional();
    }
    
    
    public Field 
    type( ManifestFieldType type ) 
    {
      return new Field( this, type );
    }

    
    public static class 
    Field 
    {
      private final Builder builder;
      private final ManifestFieldType type;
      private String name;
      private String description;
      private Integer minCount;
      private Integer maxCount;
      private boolean notInSpreadsheet = false;
      private boolean requiredInSpreadsheet = false;
      private List<ManifestFieldProcessor> processors = new ArrayList<>();
      private String default_value;
      
      
      private
      Field( Builder builder, ManifestFieldType type )
      {
        this.builder = builder;
        this.type    = type;
      }

      
      public Field 
      name( String name )
      {
        this.name = name;
        return this;
      }

      
      public Field 
      desc( String description ) 
      {
        this.description = description;
        return this;
      }
      

      public Field
      defaultValue( String value )
      {
          this.default_value = value;
          return this;
      }
      
      
      public Field 
      optional() 
      {
        this.minCount = 0;
        this.maxCount = 1;
        return this;
      }

      
      public Field 
      optional( int maxCount )
      {
        this.minCount = 0;
        this.maxCount = maxCount;
        return this;
      }

      
      public Field 
      required() 
      {
        this.minCount = 1;
        this.maxCount = 1;
        return this;
      }

      
      public Field 
      notInSpreadsheet() 
      {
        this.notInSpreadsheet = true;
        return this;
      }

      
      public Field 
      requiredInSpreadsheet() 
      {
        this.requiredInSpreadsheet = true;
        return this;
      }

      
      public Field 
      processor( ManifestFieldProcessor... processors ) 
      {
        this.processors.addAll( Arrays.stream( processors )
                                      .filter( Objects::nonNull )
                                      .collect( Collectors.toList() ) );
        return this;
      }

      
      public Builder 
      and() 
      {
        add();
        return builder;
      }

      
      public List<ManifestFieldDefinition> 
      build() 
      {
        add();
        return builder.fields;
      }

      
      private void 
      add() 
      {
        int spreadsheetMinCount = minCount;
        int spreadsheetMaxCount = maxCount;
        
        if( notInSpreadsheet )
        {
          spreadsheetMinCount = 0;
          spreadsheetMaxCount = 0;
        } else if( requiredInSpreadsheet ) 
        {
          spreadsheetMinCount = 1;
        }
        
        if( null != default_value ) 
        {
            this.minCount = 0;
            spreadsheetMinCount = 0;
        }
        
        builder.fields.add( new ManifestFieldDefinition( name, 
                                                         description, 
                                                         type, 
                                                         minCount, 
                                                         maxCount, 
                                                         spreadsheetMinCount, 
                                                         spreadsheetMaxCount, 
                                                         processors,
                                                         default_value ) );
      }
    }
  }
  
  
  public String
  toString()
  {
      return String.format( "%s(%s)[ %d, %d ]", getName(), String.valueOf( getType() ), getMinCount(), getMaxCount() );
  }
  
}
