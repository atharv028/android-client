package com.mifos.api.datamanager

import com.mifos.api.BaseApiManager
import com.mifos.api.GenericResponse
import com.mifos.api.local.databasehelper.DatabaseHelperClient
import com.mifos.objects.accounts.ClientAccounts
import com.mifos.objects.client.*
import com.mifos.objects.noncore.Identifier
import com.mifos.objects.noncore.IdentifierCreationResponse
import com.mifos.objects.noncore.IdentifierPayload
import com.mifos.objects.noncore.IdentifierTemplate
import com.mifos.objects.templates.clients.ClientsTemplate
import com.mifos.utils.PrefManager
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This DataManager is for Managing Client API, In which Request is going to Server
 * and In Response, We are getting Client API Observable Response using Retrofit2 .
 * Created by Atharv Tare on 23/03/23.
 */
@Singleton
class DataManagerClient @Inject constructor(
    val mBaseApiManager: BaseApiManager,
    val mDatabaseHelperClient: DatabaseHelperClient
) {
    /**
     * This Method sending the Request to REST API if UserStatus is 0 and
     * get list of the clients. The response is pass to the DatabaseHelperClient
     * that save the response in Database different thread and next pass the response to
     * Presenter to show in the view
     *
     *
     * If the offset is zero and UserStatus is 1 then fetch all clients list and show on the view.
     * else if offset is not zero and UserStatus is 1 then return default empty response to
     * presenter
     *
     * @param paged  True Enable the Pagination of the client list REST API
     * @param offset Value give from which position Fetch ClientList
     * @param limit  Maximum Number of clients will come in response
     * @return Client List from offset to max Limit
     */
    fun getAllClients(paged: Boolean, offset: Int, limit: Int): Observable<Page<Client>> {
        return when (PrefManager.getUserStatus()) {
            0 -> mBaseApiManager.clientsApi.getAllClients(paged, offset, limit)
                .concatMap { clientPage ->
                    Observable.just(
                        clientPage
                    )
                }
            1 -> {
                /**
                 * Return All Clients List from DatabaseHelperClient only one time.
                 * If offset is zero this means this is first request and
                 * return all clients from DatabaseHelperClient
                 */
                if (offset == 0) mDatabaseHelperClient.readAllClients() else Observable.just(Page())
            }
            else -> Observable.just(Page())
        }
    }

    /**
     * This Method Request to the DatabaseHelperClient and DatabaseHelperClient Read the All
     * clients from Client_Table and give the response Page of List of Client
     *
     * @return Page of Client List
     */
    val allDatabaseClients: Observable<Page<Client>>
        get() = mDatabaseHelperClient.readAllClients()

    /**
     * This Method
     *
     * @param clientId for Query in database or REST API request.
     * @return The Client Details
     */
    fun getClient(clientId: Int): Observable<Client> {
        return when (PrefManager.getUserStatus()) {
            0 -> mBaseApiManager.clientsApi.getClient(clientId)
                .concatMap { client ->
                    Observable.just(
                        client
                    )
                }
            1 ->
                /**
                 * Return Clients from DatabaseHelperClient only one time.
                 */
                mDatabaseHelperClient.getClient(clientId)
            else -> Observable.just(Client())
        }
    }

    fun syncClientInDatabase(client: Client?): Observable<Client> {
        return mDatabaseHelperClient.saveClient(client)
    }

    /**
     * This Method Checks the User Status and as accordingly call to Database Helper or Client
     * Retrofit Service.
     * If User is Online then this Client DataManager Request to Client Retrofit to make REST API
     * Request for Client Account.
     * If User if Offline Client DataManager request to Client DatabaseHelper to make Transaction
     * for retrieving the Clients Account.
     *
     * @param clientId Client Id
     * @return All Clients Account, Like Savings, Loan etc Accounts.
     */
    fun getClientAccounts(clientId: Int): Observable<ClientAccounts> {
        return when (PrefManager.getUserStatus()) {
            0 -> mBaseApiManager.clientsApi.getClientAccounts(clientId)
            1 ->
                /**
                 * Return Clients from DatabaseHelperClient only one time.
                 */
                mDatabaseHelperClient.realClientAccounts(clientId)
            else -> Observable.just(ClientAccounts())
        }
    }

    /**
     * This Method Fetching the Client Accounts (Loan, saving, etc Accounts ) from REST API
     * and get the ClientAccounts and then Saving all Accounts into the Database and give the
     * ClientAccount in return.
     *
     * @param clientId Client Id
     * @return ClientAccounts
     */
    fun syncClientAccounts(clientId: Int): Observable<ClientAccounts> {
        return mBaseApiManager.clientsApi.getClientAccounts(clientId)
            .concatMap { clientAccounts ->
                mDatabaseHelperClient.saveClientAccounts(
                    clientAccounts,
                    clientId
                )
            }
    }

    /**
     * This Method for removing the Client Image from his profile on server
     * if its response is true the client does not have any profile Image and if
     * response is false then failed to update the client image from server profile.
     * There can any problem during updating the client image like Network error.
     *
     * @param clientId Client ID
     * @return ResponseBody is the Retrofit 2 response
     */
    fun deleteClientImage(clientId: Int): Observable<ResponseBody> {
        return mBaseApiManager.clientsApi.deleteClientImage(clientId)
    }

    /**
     * This Method will be called when ever user want to update the client profile image.
     * The response of the updating the client image can be true or false its depends upon the
     * network and right choice image file.
     *
     * @param id   Client Id
     * @param file MultipartBody of the Image file
     * @return ResponseBody is the Retrofit 2 response
     */
    fun uploadClientImage(id: Int, file: MultipartBody.Part?): Observable<ResponseBody> {
        return mBaseApiManager.clientsApi.uploadClientImage(id, file)
    }
    /**
     * Return Clients from DatabaseHelperClient only one time.
     */
    /**
     * This Method will be called when ever user create the client. if user is in online mode
     * then request goes to the server to get the client template and in response client template
     * saves in table in background and return the Observable.just(clientTemplate) to the presenter
     * and if user in offline mode then we load the client Template from Database.
     *
     * @return ClientTemplate
     */
    val clientTemplate: Observable<ClientsTemplate>
        get() = when (PrefManager.getUserStatus()) {
            0 -> mBaseApiManager.clientsApi.clientTemplate
                .concatMap { clientsTemplate ->
                    mDatabaseHelperClient.saveClientTemplate(
                        clientsTemplate
                    )
                }
            1 ->
                /**
                 * Return Clients from DatabaseHelperClient only one time.
                 */
                /**
                 * Return Clients from DatabaseHelperClient only one time.
                 */
                mDatabaseHelperClient.readClientTemplate()
            else -> Observable.just(ClientsTemplate())
        }

    /**
     * This Method create the client by making directly request to server when User is Online
     * and give response client type is created otherwise give error string.
     * if user if offline mode then client details failed by user is saved into Database directly.
     *
     * @param clientPayload Client details filled by user
     * @return Client
     */
    fun createClient(clientPayload: ClientPayload?): Observable<Client>? {
        return when (PrefManager.getUserStatus()) {
            0 -> mBaseApiManager.clientsApi.createClient(clientPayload)
                .concatMap { client ->
                    Observable.just(
                        client
                    )
                }
            1 ->
                /**
                 * If user is in offline mode and he is making client. client payload will be saved
                 * in Database for future synchronization to sever.
                 */
                mDatabaseHelperClient.saveClientPayloadToDB(clientPayload)
            else -> null
        }
    }

    /**
     * Loading All Client payload from database to sync to the server
     *
     * @return List<ClientPayload></ClientPayload>>
     */
    val allDatabaseClientPayload: Observable<List<ClientPayload>>
        get() = mDatabaseHelperClient.readAllClientPayload()

    /**
     * This method will called when user is syncing the client created from Database.
     * whenever a client is synced then request goes to Database to delete that client form
     * Database and reload the list from Database and update the list in UI
     *
     * @param id of the clientPayload in Database
     * @return List<ClientPayload></ClientPayload>>
     */
    fun deleteAndUpdatePayloads(
        id: Int,
        clientCreationTIme: Long
    ): Observable<List<ClientPayload>> {
        return mDatabaseHelperClient.deleteAndUpdatePayloads(id, clientCreationTIme)
    }

    /**
     * This Method for Updating the Client Payload into the Database.
     *
     * @param clientPayload ClientPayload
     * @return ClientPayload
     */
    fun updateClientPayload(clientPayload: ClientPayload?): Observable<ClientPayload> {
        return mDatabaseHelperClient.updateDatabaseClientPayload(clientPayload)
    }

    /**
     * This Method is for fetching the Client identifier from the REST API.
     *
     * @param clientId Client Id
     * @return List<Identifier>
    </Identifier> */
    fun getClientIdentifiers(clientId: Int): Observable<List<Identifier>> {
        return mBaseApiManager.clientsApi.getClientIdentifiers(clientId)
    }

    /**
     * This Method is, for creating the Client Identifier.
     *
     * @param clientId          Client Id
     * @param identifierPayload IdentifierPayload
     * @return IdentifierCreationResponse
     */
    fun createClientIdentifier(
        clientId: Int, identifierPayload: IdentifierPayload?
    ): Observable<IdentifierCreationResponse> {
        return mBaseApiManager.clientsApi.createClientIdentifier(clientId, identifierPayload)
    }

    /**
     * This Method is, for fetching the Client Identifier Template.
     *
     * @param clientId Client Id
     * @return IdentifierTemplate
     */
    fun getClientIdentifierTemplate(clientId: Int): Observable<IdentifierTemplate> {
        return mBaseApiManager.clientsApi.getClientIdentifierTemplate(clientId)
    }

    /**
     * This Method is for deleting the Client Identifier.
     *
     * @param clientId     Client Id
     * @param identifierId Identifier Id
     * @return GenericResponse
     */
    fun deleteClientIdentifier(clientId: Int, identifierId: Int): Observable<GenericResponse> {
        return mBaseApiManager.clientsApi.deleteClientIdentifier(clientId, identifierId)
    }

    /**
     * This Method is fetching the Client Pinpoint location from the DataTable
     * "client_pinpoint_location"
     *
     * @param clientId Client Id
     * @return ClientAddressResponse
     */
    fun getClientPinpointLocations(clientId: Int): Observable<List<ClientAddressResponse>> {
        return mBaseApiManager.clientsApi.getClientPinpointLocations(clientId)
    }

    /**
     * This Method is adding the new address in the DataTable "client_pinpoint_location"
     *
     * @param clientId Client Id
     * @param address  Client Address
     * @return GenericResponse
     */
    fun addClientPinpointLocation(
        clientId: Int,
        address: ClientAddressRequest?
    ): Observable<GenericResponse> {
        return mBaseApiManager.clientsApi.addClientPinpointLocation(clientId, address)
    }

    /**
     * This Method is deleting the client address from the DataTable "client_pinpoint_location"
     *
     * @param apptableId
     * @param datatableId
     * @return GenericResponse
     */
    fun deleteClientAddressPinpointLocation(
        apptableId: Int,
        datatableId: Int
    ): Observable<GenericResponse> {
        return mBaseApiManager.clientsApi
            .deleteClientPinpointLocation(apptableId, datatableId)
    }

    /**
     * This Method is updating the client address from the DataTable "client_pinpoint_location"
     *
     * @param apptableId
     * @param datatableId
     * @param address     Client Address
     * @return GenericResponse
     */
    fun updateClientPinpointLocation(
        apptableId: Int,
        datatableId: Int,
        address: ClientAddressRequest?
    ): Observable<GenericResponse> {
        return mBaseApiManager.clientsApi.updateClientPinpointLocation(
            apptableId, datatableId, address
        )
    }

    /**
     * This method is activating the client
     *
     * @param clientId
     * @return GenericResponse
     */
    fun activateClient(
        clientId: Int,
        clientActivate: ActivatePayload?
    ): Observable<GenericResponse> {
        return mBaseApiManager.clientsApi.activateClient(clientId, clientActivate)
    }
}
