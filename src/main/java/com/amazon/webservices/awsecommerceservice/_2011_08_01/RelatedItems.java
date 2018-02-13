//
// Diese Datei wurde mit der JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.0-b170531.0717 generiert 
// Siehe <a href="https://jaxb.java.net/">https://jaxb.java.net/</a> 
// Änderungen an dieser Datei gehen bei einer Neukompilierung des Quellschemas verloren. 
// Generiert: 2018.01.05 um 01:41:30 PM CET 
//


package com.amazon.webservices.awsecommerceservice._2011_08_01;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java-Klasse für anonymous complex type.
 * 
 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="Relationship" minOccurs="0"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="Parents"/&gt;
 *               &lt;enumeration value="Children"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="RelationshipType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="RelatedItemCount" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" minOccurs="0"/&gt;
 *         &lt;element name="RelatedItemPageCount" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" minOccurs="0"/&gt;
 *         &lt;element name="RelatedItemPage" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger" minOccurs="0"/&gt;
 *         &lt;element ref="{http://webservices.amazon.com/AWSECommerceService/2011-08-01}RelatedItem" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "relationship",
    "relationshipType",
    "relatedItemCount",
    "relatedItemPageCount",
    "relatedItemPage",
    "relatedItem"
})
@XmlRootElement(name = "RelatedItems")
public class RelatedItems {

    @XmlElement(name = "Relationship")
    protected String relationship;
    @XmlElement(name = "RelationshipType")
    protected String relationshipType;
    @XmlElement(name = "RelatedItemCount")
    @XmlSchemaType(name = "nonNegativeInteger")
    protected BigInteger relatedItemCount;
    @XmlElement(name = "RelatedItemPageCount")
    @XmlSchemaType(name = "nonNegativeInteger")
    protected BigInteger relatedItemPageCount;
    @XmlElement(name = "RelatedItemPage")
    @XmlSchemaType(name = "nonNegativeInteger")
    protected BigInteger relatedItemPage;
    @XmlElement(name = "RelatedItem")
    protected List<RelatedItem> relatedItem;

    /**
     * Ruft den Wert der relationship-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRelationship() {
        return relationship;
    }

    /**
     * Legt den Wert der relationship-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRelationship(String value) {
        this.relationship = value;
    }

    /**
     * Ruft den Wert der relationshipType-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRelationshipType() {
        return relationshipType;
    }

    /**
     * Legt den Wert der relationshipType-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRelationshipType(String value) {
        this.relationshipType = value;
    }

    /**
     * Ruft den Wert der relatedItemCount-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getRelatedItemCount() {
        return relatedItemCount;
    }

    /**
     * Legt den Wert der relatedItemCount-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setRelatedItemCount(BigInteger value) {
        this.relatedItemCount = value;
    }

    /**
     * Ruft den Wert der relatedItemPageCount-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getRelatedItemPageCount() {
        return relatedItemPageCount;
    }

    /**
     * Legt den Wert der relatedItemPageCount-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setRelatedItemPageCount(BigInteger value) {
        this.relatedItemPageCount = value;
    }

    /**
     * Ruft den Wert der relatedItemPage-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getRelatedItemPage() {
        return relatedItemPage;
    }

    /**
     * Legt den Wert der relatedItemPage-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setRelatedItemPage(BigInteger value) {
        this.relatedItemPage = value;
    }

    /**
     * Gets the value of the relatedItem property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the relatedItem property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRelatedItem().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link RelatedItem }
     * 
     * 
     */
    public List<RelatedItem> getRelatedItem() {
        if (relatedItem == null) {
            relatedItem = new ArrayList<RelatedItem>();
        }
        return this.relatedItem;
    }

}
